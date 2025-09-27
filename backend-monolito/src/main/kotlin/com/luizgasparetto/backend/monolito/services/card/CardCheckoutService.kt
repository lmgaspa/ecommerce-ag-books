package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.dto.card.CardCartItemDto
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutResponse
import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderItem
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import java.math.RoundingMode

@Service
@Transactional
class CardCheckoutService(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val cardService: CardService,
    private val processor: CardPaymentProcessor,
    private val cardWatcher: CardWatcher? = null
) {
    private val log = LoggerFactory.getLogger(CardCheckoutService::class.java)
    private val reserveTtlSeconds: Long = 900

    fun processCardCheckout(request: CardCheckoutRequest): CardCheckoutResponse {
        // 0) valida estoque e total no servidor
        request.cartItems.forEach { item -> bookService.validateStock(item.id, item.quantity) }
        val totalAmount = calculateTotalAmount(request.shipping, request.cartItems)

        val txid = "CARD-" + UUID.randomUUID().toString().replace("-", "").take(30)

        // 1) cria pedido base + reserva TTL
        val order = createOrderTx(request, totalAmount, txid).also {
            it.paymentMethod = "card"
            it.installments = request.installments.coerceAtLeast(1)
        }
        reserveItemsTx(order, reserveTtlSeconds)

        // 2) montar itens e cliente para Efí
        val itemsForEfi = request.cartItems.map {
            mapOf(
                "name" to it.title,
                "value" to it.price.toBigDecimal().multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).toInt(),
                "amount" to it.quantity
            )
        }
        val customer = mapOf(
            "name" to "${request.firstName} ${request.lastName}",
            "cpf" to request.cpf.filter { it.isDigit() },
            "email" to request.email,
            "phone_number" to request.phone.filter { it.isDigit() }.ifBlank { null }
        )

        val shippingCents = request.shipping.toBigDecimal()
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toInt()

        // 3) cobrança cartão (one-step) – por padrão, usando `shippings`
        val result = try {
            cardService.createOneStepCharge(
                totalAmount = totalAmount,
                items = itemsForEfi,
                paymentToken = request.paymentToken,
                installments = request.installments,
                customer = customer,
                txid = txid,
                shippingCents = shippingCents,      // manda no campo `shippings`
                addShippingAsItem = false           // mude para true se quiser como item "Frete"
            )
        } catch (e: Exception) {
            log.error("CARD: falha ao cobrar, liberando reserva. orderId={}, err={}", order.id, e.message, e)
            releaseReservationTx(order.id!!)
            return CardCheckoutResponse(
                success = false,
                message = "Pagamento não processado. Tente novamente.",
                orderId = order.id.toString(),
                chargeId = null,
                status = "FAILED"
            )
        }

        if (result.chargeId.isNullOrBlank()) {
            log.warn("CARD: cobrança não criada (sem chargeId). status={}, orderId={}", result.status, order.id)
            releaseReservationTx(order.id!!)
            return CardCheckoutResponse(
                success = false,
                message = "Não foi possível criar a cobrança do cartão. Tente novamente.",
                orderId = order.id.toString(),
                chargeId = null,
                status = if (result.status.isBlank()) "ERROR" else result.status
            )
        }

        // 4) salvar chargeId e decidir confirmação
        val fresh = orderRepository.findWithItemsById(order.id!!)
            ?: error("Order ${order.id} não encontrado após criação")
        fresh.chargeId = result.chargeId
        fresh.paymentMethod = "card"
        fresh.installments = request.installments.coerceAtLeast(1)
        orderRepository.save(fresh)

        if (result.paid && !fresh.chargeId.isNullOrBlank()) {
            processor.markPaidIfNeededByChargeId(fresh.chargeId!!)
            return CardCheckoutResponse(
                success = true,
                message = "Pagamento aprovado.",
                orderId = fresh.id.toString(),
                chargeId = fresh.chargeId,
                status = result.status
            )
        }

        // watcher opcional
        runCatching {
            val expires = requireNotNull(fresh.reserveExpiresAt).toInstant()
            if (fresh.chargeId != null && cardWatcher != null) {
                cardWatcher.watch(fresh.chargeId!!, expires)
            }
        }

        return CardCheckoutResponse(
            success = true,
            message = "Pagamento em análise/processamento.",
            orderId = fresh.id.toString(),
            chargeId = fresh.chargeId,
            status = result.status
        )
    }

    // ================== privados / util ==================

    private fun calculateTotalAmount(shipping: Double, cart: List<CardCartItemDto>): BigDecimal {
        val items = cart.fold(BigDecimal.ZERO) { acc, it ->
            acc + it.price.toBigDecimal().multiply(BigDecimal(it.quantity))
        }
        return items + shipping.toBigDecimal()
    }

    private fun createOrderTx(request: CardCheckoutRequest, totalAmount: BigDecimal, txid: String): Order {
        val order = Order(
            firstName = request.firstName,
            lastName  = request.lastName,
            email     = request.email,
            cpf       = request.cpf,
            number    = request.number,
            complement= request.complement,
            district  = request.district,
            address   = request.address,
            city      = request.city,
            state     = request.state,
            cep       = request.cep,
            phone     = request.phone,
            note      = request.note,
            total     = totalAmount,
            shipping  = request.shipping.toBigDecimal(),
            paid      = false,
            txid      = txid,
            items     = mutableListOf(),
            status    = OrderStatus.NEW
        )

        order.items = request.cartItems.map {
            OrderItem(
                bookId = it.id,
                title = it.title,
                quantity = it.quantity,
                price = it.price.toBigDecimal(),
                imageUrl = bookService.getImageUrl(it.id),
                order = order
            )
        }.toMutableList()

        val saved = orderRepository.save(order)
        log.info("CARD-TX1: order salvo id={}, txid={}", saved.id, txid)
        return saved
    }

    private fun reserveItemsTx(order: Order, ttlSeconds: Long) {
        order.items.forEach { item -> bookService.reserveOrThrow(item.bookId, item.quantity) }
        order.status = OrderStatus.WAITING
        order.reserveExpiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds)
        orderRepository.save(order)
        log.info("CARD-RESERVA: orderId={} ttl={}s expiraEm={}", order.id, ttlSeconds, order.reserveExpiresAt)
    }

    private fun releaseReservationTx(orderId: Long) {
        val order = orderRepository.findWithItemsById(orderId)
            ?: throw IllegalStateException("Order $orderId não encontrado")

        if (order.status == OrderStatus.WAITING && !order.paid) {
            order.items.forEach { item -> bookService.release(item.bookId, item.quantity) }
            order.status = OrderStatus.EXPIRED
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("CARD-RESERVA LIBERADA: orderId={}", orderId)
        }
    }
}
