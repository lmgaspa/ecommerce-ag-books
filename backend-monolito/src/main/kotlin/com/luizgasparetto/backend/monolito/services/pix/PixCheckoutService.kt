package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.dto.pix.PixCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.pix.PixCheckoutResponse
import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderItem
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Service
class PixCheckoutService(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val pixWatcher: PixWatcher,
    private val pixService: PixService,
    @Value("\${efi.pix.chave}") private val chavePix: String,
    @Value("\${checkout.reserve.ttl-seconds:900}") private val reserveTtlSeconds: Long
) {
    private val log = LoggerFactory.getLogger(PixCheckoutService::class.java)

    fun processCheckout(request: PixCheckoutRequest): PixCheckoutResponse {
        // 0) valida disponibilidade (checagem rápida)
        request.cartItems.forEach { item -> bookService.validateStock(item.id, item.quantity) }

        val totalAmount = calculateTotalAmount(request)
        val txid = UUID.randomUUID().toString().replace("-", "").take(35)

        // 1) cria pedido + itens (TX)
        val order = createOrderTx(request, totalAmount, txid)

        // 2) reserva estoque + define TTL (TX)
        reserveItemsTx(order, reserveTtlSeconds)

        // 3) cria cobrança Pix via serviço centralizado; se falhar, libera reserva
        val cob = try {
            pixService.criarPixCobranca(totalAmount, chavePix, "Pedido $txid", txid)
        } catch (e: Exception) {
            log.error("Falha ao criar QR na Efí, liberando reserva. orderId={}, txid={}, err={}", order.id, txid, e.message, e)
            releaseReservationTx(order.id!!)
            throw e
        }

        // 4) grava QR no pedido (TX)
        updateOrderWithQrTx(order.id!!, cob.pixCopiaECola, cob.imagemQrcodeBase64)

        // 5) inicia watcher até o TTL
        val expireAtInstant = requireNotNull(order.reserveExpiresAt) { "reserveExpiresAt nulo após reserva" }.toInstant()
        pixWatcher.watch(txid, expireAtInstant)

        log.info(
            "CHECKOUT PIX OK: orderId={}, txid={}, ttl={}s, expiraEm={}, qrLen={}, imgLen={}",
            order.id, txid, reserveTtlSeconds, order.reserveExpiresAt, cob.pixCopiaECola.length, cob.imagemQrcodeBase64.length
        )

        return PixCheckoutResponse(
            qrCode = cob.pixCopiaECola,
            qrCodeBase64 = cob.imagemQrcodeBase64,
            message = "Pedido gerado com sucesso",
            orderId = order.id.toString(),
            txid = txid,
            reserveExpiresAt = order.reserveExpiresAt?.toString(),
            ttlSeconds = reserveTtlSeconds
        )
    }

    // ------------------- privados -------------------

    private fun calculateTotalAmount(request: PixCheckoutRequest): BigDecimal {
        val totalBooks = request.cartItems.sumOf { it.price.toBigDecimal() * BigDecimal(it.quantity) }
        return totalBooks + request.shipping.toBigDecimal()
    }

    @Transactional
    fun createOrderTx(request: PixCheckoutRequest, totalAmount: BigDecimal, txid: String): Order {
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
        log.info("TX1: order salvo id={}, txid={}", saved.id, txid)
        return saved
    }

    /** Reserva todos os itens do pedido e define TTL da reserva. */
    @Transactional
    fun reserveItemsTx(order: Order, ttlSeconds: Long) {
        order.items.forEach { item -> bookService.reserveOrThrow(item.bookId, item.quantity) }
        order.status = OrderStatus.WAITING
        order.reserveExpiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds)
        orderRepository.save(order)
        log.info("RESERVA: orderId={} ttl={}s expiraEm={}", order.id, ttlSeconds, order.reserveExpiresAt)
    }

    /** Libera a reserva de um pedido (usado se falhar emissão de QR). */
    @Transactional
    fun releaseReservationTx(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalStateException("Order $orderId não encontrado") }
        if (order.status == OrderStatus.WAITING && !order.paid) {
            order.items.forEach { item -> bookService.release(item.bookId, item.quantity) }
            order.status = OrderStatus.EXPIRED
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("RESERVA LIBERADA: orderId={}", orderId)
        }
    }

    @Transactional
    fun updateOrderWithQrTx(orderId: Long, qrCode: String, qrB64: String) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { IllegalStateException("Order $orderId não encontrado") }
        order.qrCode = qrCode
        order.qrCodeBase64 = qrB64
        orderRepository.save(order)
        log.info("TX2: QR gravado. orderId={}", orderId)
    }
}
