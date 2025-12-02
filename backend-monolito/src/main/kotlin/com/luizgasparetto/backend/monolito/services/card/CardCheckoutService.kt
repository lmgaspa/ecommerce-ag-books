package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.dto.card.CardCartItemDto
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutResponse
import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderItem
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.OrderCouponRepository
import com.luizgasparetto.backend.monolito.models.coupon.OrderCoupon
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.coupon.CouponService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Transactional
class CardCheckoutService(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val cardService: CardService,
    private val processor: CardPaymentProcessor,
    private val couponService: CouponService,
    private val orderCouponRepository: OrderCouponRepository,
    private val cardWatcher: CardWatcher? = null
) {
    private val log = LoggerFactory.getLogger(CardCheckoutService::class.java)
    private val reserveTtlSeconds: Long = 900

    fun processCardCheckout(request: CardCheckoutRequest): CardCheckoutResponse {
        // 0) valida estoque no servidor
        request.cartItems.forEach { item ->
            bookService.validateStock(item.id, item.quantity)
        }

        // 0.1) calcula total original (sem cupom) e processa desconto
        val originalTotal = calculateOriginalTotal(request.shipping, request.cartItems)
        val (finalTotal, discountAmount) = processDiscount(request, originalTotal)

        // Log detalhado para debug
        log.info("💳 CHECKOUT CARD - Dados recebidos:")
        log.info("  - Total do request: {}", request.total)
        log.info("  - Shipping: {}", request.shipping)
        log.info("  - CouponCode: {}", request.couponCode)
        log.info("  - Discount (frontend): {}", request.discount)
        log.info("  - Total calculado (original): {}", originalTotal)
        log.info("  - Desconto aplicado: {}", discountAmount)
        log.info("  - Total final: {}", finalTotal)

        // Segurança: total final nunca pode ser zero/negativo
        if (finalTotal < BigDecimal("0.01")) {
            log.error("❌ Total final muito baixo: {} - Rejeitando checkout", finalTotal)
            throw IllegalArgumentException("Valor do pedido muito baixo. Valor mínimo: R$ 0,01")
        }

        val txid = "CARD-" + UUID.randomUUID().toString().replace("-", "").take(30)

        // 1) cria pedido base + reserva TTL
        val order = createOrderTx(request, finalTotal, discountAmount, request.couponCode, txid).also {
            it.paymentMethod = "card"
            it.installments = request.installments.coerceAtLeast(1)
        }
        reserveItemsTx(order, reserveTtlSeconds)

        // 2) monta itens e cliente para Efí,
        //    já com desconto distribuído em itens/frete
        val (itemsForEfi, shippingCentsAdjusted) = buildEfiAmounts(
            request = request,
            originalTotal = originalTotal,
            finalTotal = finalTotal
        )

        val customer = mapOf(
            "name" to "${request.firstName} ${request.lastName}",
            "cpf" to request.cpf.filter { it.isDigit() },
            "email" to request.email,
            "phone_number" to request.phone.filter { it.isDigit() }.ifBlank { null }
        )

        // 3) cobrança cartão (one-step) – usando `shippings` com valor já ajustado
        val result = try {
            cardService.createOneStepCharge(
                totalAmount = finalTotal,          // já com desconto
                items = itemsForEfi,              // itens em centavos, pós-desconto
                paymentToken = request.paymentToken,
                installments = request.installments,
                customer = customer,
                txid = txid,
                shippingCents = shippingCentsAdjusted,
                addShippingAsItem = false         // se quiser mover frete pra item, trocar pra true
            )
        } catch (e: Exception) {
            log.error(
                "CARD: falha ao cobrar, liberando reserva. orderId={}, err={}",
                order.id, e.message, e
            )
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
            log.warn(
                "CARD: cobrança não criada (sem chargeId). status={}, orderId={}",
                result.status, order.id
            )
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
                status = result.status,
                reserveExpiresAt = fresh.reserveExpiresAt?.toString(),
                ttlSeconds = reserveTtlSeconds,
                warningAt = 60,
                securityWarningAt = 60
            )
        }

        // watcher opcional (pagamento em análise)
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
            status = result.status,
            reserveExpiresAt = fresh.reserveExpiresAt?.toString(),
            ttlSeconds = reserveTtlSeconds,
            warningAt = 60,
            securityWarningAt = 60
        )
    }

    // ================== privados / util ==================

    /**
     * Calcula o total original (sem desconto):
     * soma dos itens + frete.
     */
    private fun calculateOriginalTotal(
        shipping: Double,
        cart: List<CardCartItemDto>
    ): BigDecimal {
        val items = cart.fold(BigDecimal.ZERO) { acc, it ->
            acc + it.price.toBigDecimal().multiply(BigDecimal(it.quantity))
        }
        return items + shipping.toBigDecimal()
    }

    /**
     * Aplica regras de cupom no backend.
     * - usa total original como base
     * - valida cupom
     * - escolhe o menor desconto entre frontend e backend
     * - garante que finalTotal >= 0,01
     */
    private fun processDiscount(
        request: CardCheckoutRequest,
        originalTotal: BigDecimal
    ): Pair<BigDecimal, BigDecimal> {
        if (request.couponCode.isNullOrBlank()) {
            return Pair(originalTotal, BigDecimal.ZERO)
        }

        val couponValidation = couponService.validateCoupon(
            CouponService.CouponValidationRequest(
                code = request.couponCode,
                orderTotal = originalTotal,
                userEmail = request.email
            )
        )

        if (!couponValidation.valid) {
            log.warn(
                "Cupom inválido: {} - {}",
                request.couponCode,
                couponValidation.errorMessage
            )
            return Pair(originalTotal, BigDecimal.ZERO)
        }

        val frontendDiscount = request.discount?.toBigDecimal() ?: BigDecimal.ZERO
        val calculatedDiscount = couponValidation.discountAmount

        val finalDiscount = if (frontendDiscount > BigDecimal.ZERO) {
            minOf(frontendDiscount, calculatedDiscount)
        } else {
            calculatedDiscount
        }

        val maxAllowedDiscount = originalTotal - BigDecimal("0.01")
        val limitedDiscount = minOf(finalDiscount, maxAllowedDiscount)

        val finalTotal = originalTotal - limitedDiscount

        log.info("💳 PROCESSAMENTO DESCONTO CARD:")
        log.info("  - Desconto frontend: {}", frontendDiscount)
        log.info("  - Desconto calculado: {}", calculatedDiscount)
        log.info("  - Desconto final: {}", limitedDiscount)
        log.info("  - Total final: {}", finalTotal)

        return Pair(finalTotal, limitedDiscount)
    }

    /**
     * Cria o pedido no banco, com:
     * - total = finalTotal (já com cupom)
     * - discountAmount preenchido
     * - order_coupons com originalTotal/finalTotal/discountAmount.
     */
    private fun createOrderTx(
        request: CardCheckoutRequest,
        totalAmount: BigDecimal,
        discountAmount: BigDecimal,
        couponCode: String?,
        txid: String
    ): Order {
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
            couponCode = couponCode,
            discountAmount = if (discountAmount > BigDecimal.ZERO) discountAmount else null,
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

        if (couponCode != null && discountAmount > BigDecimal.ZERO) {
            val coupon = couponService.getCouponByCode(couponCode)
            if (coupon != null) {
                val orderCoupon = OrderCoupon(
                    order = saved,
                    coupon = coupon,
                    originalTotal = calculateOriginalTotal(request.shipping, request.cartItems),
                    discountAmount = discountAmount,
                    finalTotal = totalAmount
                )
                orderCouponRepository.save(orderCoupon)
                log.info(
                    "Cupom aplicado: orderId={}, couponCode={}, discount={}",
                    saved.id, couponCode, discountAmount
                )
            }
        }

        log.info("CARD-TX1: order salvo id={}, txid={}", saved.id, txid)
        return saved
    }

    private fun reserveItemsTx(order: Order, ttlSeconds: Long) {
        order.items.forEach { item ->
            bookService.reserveOrThrow(item.bookId, item.quantity)
        }
        order.status = OrderStatus.WAITING
        order.reserveExpiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds)
        orderRepository.save(order)
        log.info(
            "CARD-RESERVA: orderId={} ttl={}s expiraEm={}",
            order.id, ttlSeconds, order.reserveExpiresAt
        )
    }

    private fun releaseReservationTx(orderId: Long) {
        val order = orderRepository.findWithItemsById(orderId)
            ?: throw IllegalStateException("Order $orderId não encontrado")

        if (order.status == OrderStatus.WAITING && !order.paid) {
            order.items.forEach { item ->
                bookService.release(item.bookId, item.quantity)
            }
            order.status = OrderStatus.EXPIRED
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("CARD-RESERVA LIBERADA: orderId={}", orderId)
        }
    }

    /**
     * 🔧 Ponto "hard":
     * Distribui o desconto (originalTotal - finalTotal) em centavos
     * entre frete e itens, de forma que:
     *
     *    soma(itens_ajustados) + frete_ajustado == finalTotal (em centavos)
     *
     * Sem alterar como o pedido é salvo no banco.
     */
    private fun buildEfiAmounts(
        request: CardCheckoutRequest,
        originalTotal: BigDecimal,
        finalTotal: BigDecimal
    ): Pair<List<Map<String, Any>>, Int> {
        val discountCents = originalTotal
            .subtract(finalTotal)
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toInt()

        // Base em centavos (sem desconto)
        val baseShippingCents = request.shipping.toBigDecimal()
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .toInt()

        val baseItemsCents = request.cartItems.map { item ->
            item.price.toBigDecimal()
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toInt() to item
        }

        if (discountCents <= 0) {
            // Sem desconto: usa tudo como veio
            val itemsForEfi = baseItemsCents.map { (cents, item) ->
                mapOf(
                    "name" to item.title,
                    "value" to cents,
                    "amount" to item.quantity
                )
            }
            return Pair(itemsForEfi, baseShippingCents)
        }

        var remaining = discountCents

        // 1ª etapa: tenta tirar do frete
        val usedOnShipping = minOf(remaining, baseShippingCents)
        val shippingAdjusted = baseShippingCents - usedOnShipping
        remaining -= usedOnShipping

        // 2ª etapa: distribui o resto nos itens (simples: vai consumindo item a item)
        val adjustedItemsCents = baseItemsCents.mapIndexed { index, (cents, item) ->
            if (remaining <= 0) {
                cents to item
            } else {
                val maxDiscountOnItem = cents // pode zerar o item se necessário
                val use = if (index == baseItemsCents.lastIndex) {
                    // no último item, consome tudo que sobrou (até o máximo possível)
                    minOf(remaining, maxDiscountOnItem)
                } else {
                    minOf(remaining, maxDiscountOnItem)
                }
                remaining -= use
                (cents - use) to item
            }
        }

        val finalItems = adjustedItemsCents.map { (cents, item) ->
            mapOf(
                "name" to item.title,
                "value" to cents,
                "amount" to item.quantity
            )
        }

        val totalOriginalCents =
            baseItemsCents.sumOf { it.first * it.second.quantity } + baseShippingCents
        val totalAdjustedCents =
            adjustedItemsCents.sumOf { it.first * it.second.quantity } + shippingAdjusted

        log.info(
            "CARD-EFI-LINES: originalCents={} adjustedCents={} discountCents={}",
            totalOriginalCents, totalAdjustedCents, discountCents
        )

        return Pair(finalItems, shippingAdjusted)
    }
}
