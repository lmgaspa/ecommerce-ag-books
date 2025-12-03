package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.dto.card.CardCartItemDto
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutResponse
import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderItem
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.coupon.CouponService
import com.luizgasparetto.backend.monolito.repositories.OrderCouponRepository
import com.luizgasparetto.backend.monolito.models.coupon.OrderCoupon
import org.slf4j.Logger
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
    private val couponService: CouponService,
    private val orderCouponRepository: OrderCouponRepository,
    private val cardWatcher: CardWatcher? = null
) {
    private val log = LoggerFactory.getLogger(CardCheckoutService::class.java)
    private val reserveTtlSeconds: Long = 900

    fun processCardCheckout(request: CardCheckoutRequest): CardCheckoutResponse {
        // 0) valida estoque e total no servidor
        request.cartItems.forEach { item -> bookService.validateStock(item.id, item.quantity) }
        
        // 0.1) calcula total original e processa desconto
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
        
        // Validação de segurança: garantir que o total final seja sempre >= 0.01
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

        // 2) distribuir desconto entre itens e frete para alinhar com finalTotal
        val distributionResult = DiscountDistributionHelper.distributeDiscount(
            cartItems = request.cartItems,
            shipping = request.shipping,
            discountAmount = discountAmount,
            finalTotal = finalTotal,
            logger = log
        )
        
        val customer = mapOf(
            "name" to "${request.firstName} ${request.lastName}",
            "cpf" to request.cpf.filter { it.isDigit() },
            "email" to request.email,
            "phone_number" to request.phone.filter { it.isDigit() }.ifBlank { null }
        )

        // 3) cobrança cartão (one-step) – por padrão, usando `shippings`
        val result = try {
            cardService.createOneStepCharge(
                totalAmount = finalTotal,
                items = distributionResult.itemsForEfi,
                paymentToken = request.paymentToken,
                installments = request.installments,
                customer = customer,
                txid = txid,
                shippingCents = distributionResult.shippingCents,
                addShippingAsItem = false
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
            status = result.status,
            reserveExpiresAt = fresh.reserveExpiresAt?.toString(),
            ttlSeconds = reserveTtlSeconds,
            warningAt = 60, // Avisar quando faltar 60 segundos
            securityWarningAt = 60 // INVALIDAR quando faltar 60 segundos (segurança máxima)
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
            status = result.status,
            reserveExpiresAt = fresh.reserveExpiresAt?.toString(),
            ttlSeconds = reserveTtlSeconds,
            warningAt = 60, // Avisar quando faltar 60 segundos
            securityWarningAt = 60 // INVALIDAR quando faltar 60 segundos (segurança máxima)
        )
    }

    // ================== privados / util ==================

    private fun calculateOriginalTotal(shipping: Double, cart: List<CardCartItemDto>): BigDecimal {
        val items = cart.fold(BigDecimal.ZERO) { acc, it ->
            acc + it.price.toBigDecimal().multiply(BigDecimal(it.quantity))
        }
        return items + shipping.toBigDecimal()
    }

    private fun processDiscount(request: CardCheckoutRequest, originalTotal: BigDecimal): Pair<BigDecimal, BigDecimal> {
        // Se não há cupom, não há desconto
        if (request.couponCode.isNullOrBlank()) {
            return Pair(originalTotal, BigDecimal.ZERO)
        }

        // Validar cupom primeiro
        val couponValidation = couponService.validateCoupon(
            CouponService.CouponValidationRequest(
                code = request.couponCode,
                orderTotal = originalTotal,
                userEmail = request.email
            )
        )

        if (!couponValidation.valid) {
            log.warn("Cupom inválido: {} - {}", request.couponCode, couponValidation.errorMessage)
            return Pair(originalTotal, BigDecimal.ZERO)
        }

        // SEGURANÇA: O backend é a fonte da verdade para o desconto.
        // Ignoramos completamente o request.discount do frontend para evitar manipulação.
        // O frontend deve enviar apenas o couponCode, e o backend recalcula tudo do zero.
        val calculatedDiscount = couponValidation.discountAmount

        // Garantir que o desconto não seja maior que o total (deixando pelo menos R$ 0,01)
        val maxAllowedDiscount = originalTotal - BigDecimal("0.01")
        val limitedDiscount = minOf(calculatedDiscount, maxAllowedDiscount)

        val finalTotal = originalTotal - limitedDiscount

        log.info("💳 PROCESSAMENTO DESCONTO CARD:")
        log.info("  - Desconto frontend (IGNORADO por segurança): {}", request.discount)
        log.info("  - Desconto calculado pelo backend: {}", calculatedDiscount)
        log.info("  - Desconto final aplicado: {}", limitedDiscount)
        log.info("  - Total final: {}", finalTotal)

        return Pair(finalTotal, limitedDiscount)
    }

    private fun createOrderTx(request: CardCheckoutRequest, totalAmount: BigDecimal, discountAmount: BigDecimal, couponCode: String?, txid: String): Order {
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
        
        // Salvar cupom aplicado se houver
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
                log.info("Cupom aplicado: orderId={}, couponCode={}, discount={}", saved.id, couponCode, discountAmount)
            }
        }
        
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

    /**
     * Helper class to distribute discount across items and shipping so that
     * the sum of adjusted values matches the finalTotal exactly in cents.
     * 
     * Strategy (Option A - Simple):
     * 1. First try to apply entire discount to shipping
     * 2. If discount <= shipping, reduce shipping only
     * 3. If discount > shipping, reduce shipping to 0 and distribute remaining discount proportionally among items
     */
    private object DiscountDistributionHelper {
        data class DistributionResult(
            val itemsForEfi: List<Map<String, Any>>,
            val shippingCents: Int
        )

        fun distributeDiscount(
            cartItems: List<CardCartItemDto>,
            shipping: Double,
            discountAmount: BigDecimal,
            finalTotal: BigDecimal,
            logger: Logger
        ): DistributionResult {
            // Convert original values to cents
            val itemsWithCents = cartItems.map { item ->
                val unitPriceCents = item.price.toBigDecimal()
                    .setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toInt()
                val lineTotalCents = unitPriceCents * item.quantity
                ItemWithCents(
                    dto = item,
                    unitPriceCents = unitPriceCents,
                    lineTotalCents = lineTotalCents
                )
            }

            val shippingCents = shipping.toBigDecimal()
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toInt()

            val originalCentsTotal = itemsWithCents.sumOf { it.lineTotalCents } + shippingCents
            val discountCents = discountAmount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toInt()
            val targetCents = originalCentsTotal - discountCents

            logger.info("💰 DISTRIBUIÇÃO DE DESCONTO:")
            logger.info("  - Total original (cents): {}", originalCentsTotal)
            logger.info("  - Desconto (cents): {}", discountCents)
            logger.info("  - Total alvo (cents): {}", targetCents)

            // If no discount, return original values
            if (discountCents <= 0) {
                return DistributionResult(
                    itemsForEfi = itemsWithCents.map { item ->
                        mapOf(
                            "name" to item.dto.title,
                            "value" to item.unitPriceCents,
                            "amount" to item.dto.quantity
                        )
                    },
                    shippingCents = shippingCents
                )
            }

            // Strategy: Apply discount to shipping first, then to items if needed
            val (adjustedShippingCents, remainingDiscountCents) = if (discountCents <= shippingCents) {
                // Discount fits entirely in shipping
                Pair(shippingCents - discountCents, 0)
            } else {
                // Discount exceeds shipping, reduce shipping to 0
                Pair(0, discountCents - shippingCents)
            }

            // Distribute remaining discount proportionally among items
            val adjustedItems = if (remainingDiscountCents > 0) {
                distributeDiscountAmongItems(itemsWithCents, remainingDiscountCents, logger)
            } else {
                // No discount on items, keep original values
                itemsWithCents.map { item ->
                    AdjustedItem(
                        dto = item.dto,
                        adjustedUnitPriceCents = item.unitPriceCents,
                        adjustedLineTotalCents = item.lineTotalCents
                    )
                }
            }

            // Build final items for Efí
            val itemsForEfi = adjustedItems.map { item ->
                mapOf(
                    "name" to item.dto.title,
                    "value" to item.adjustedUnitPriceCents,
                    "amount" to item.dto.quantity
                )
            }

            val finalSumCents = adjustedItems.sumOf { it.adjustedLineTotalCents } + adjustedShippingCents

            logger.info("💰 DISTRIBUIÇÃO FINAL:")
            logger.info("  - Frete ajustado (cents): {}", adjustedShippingCents)
            adjustedItems.forEachIndexed { index, item ->
                logger.info("  - Item {}: {} x {} = {} cents", index + 1, item.adjustedUnitPriceCents, item.dto.quantity, item.adjustedLineTotalCents)
            }
            logger.info("  - Soma final (cents): {}", finalSumCents)
            logger.info("  - Total alvo (cents): {}", targetCents)
            logger.info("  - ✅ Soma final {} total alvo", if (finalSumCents == targetCents) "==" else "!=")

            return DistributionResult(
                itemsForEfi = itemsForEfi,
                shippingCents = adjustedShippingCents
            )
        }

        private data class ItemWithCents(
            val dto: CardCartItemDto,
            val unitPriceCents: Int,
            val lineTotalCents: Int
        )

        private data class AdjustedItem(
            val dto: CardCartItemDto,
            val adjustedUnitPriceCents: Int,
            val adjustedLineTotalCents: Int
        )

        /**
         * Distributes discount proportionally among items, ensuring:
         * - No line goes negative
         * - Integer cents are properly handled
         * - Total discount matches remainingDiscountCents exactly
         */
        private fun distributeDiscountAmongItems(
            items: List<ItemWithCents>,
            remainingDiscountCents: Int,
            logger: Logger
        ): List<AdjustedItem> {
            if (items.isEmpty()) {
                return emptyList()
            }

            val itemsTotalCents = items.sumOf { it.lineTotalCents }
            val targetItemsSumCents = itemsTotalCents - remainingDiscountCents
            
            if (itemsTotalCents <= 0 || targetItemsSumCents < 0) {
                logger.warn("⚠️ Total de itens é zero ou desconto excede total, não é possível distribuir desconto")
                return items.map { item ->
                    AdjustedItem(
                        dto = item.dto,
                        adjustedUnitPriceCents = item.unitPriceCents,
                        adjustedLineTotalCents = item.lineTotalCents
                    )
                }
            }

            // Calculate proportional discount per item (using floor to avoid over-discounting)
            val discountsPerItem = mutableListOf<Int>()
            var totalDiscountApplied = 0

            items.forEachIndexed { index, item ->
                val proportionalDiscount = if (itemsTotalCents > 0) {
                    (item.lineTotalCents.toBigDecimal() * remainingDiscountCents.toBigDecimal())
                        .divide(itemsTotalCents.toBigDecimal(), 0, RoundingMode.FLOOR)
                        .toInt()
                        .coerceAtMost(item.lineTotalCents)
                } else {
                    0
                }
                discountsPerItem.add(proportionalDiscount)
                totalDiscountApplied += proportionalDiscount
            }

            // Distribute remaining cents (due to rounding) to ensure exact match
            var remainingToDistribute = remainingDiscountCents - totalDiscountApplied
            if (remainingToDistribute > 0) {
                // Distribute to items with capacity, starting from largest remaining capacity
                val itemsWithCapacity = items.mapIndexedNotNull { index, item ->
                    val discountApplied = discountsPerItem[index]
                    val remainingCapacity = item.lineTotalCents - discountApplied
                    if (remainingCapacity > 0) {
                        index to remainingCapacity
                    } else {
                        null
                    }
                }.sortedByDescending { it.second }

                itemsWithCapacity.forEach { (index, _) ->
                    if (remainingToDistribute > 0) {
                        discountsPerItem[index]++
                        remainingToDistribute--
                    }
                }
            }

            // Build adjusted items with exact line totals
            val adjustedItems = items.mapIndexed { index, item ->
                val discountApplied = discountsPerItem[index]
                val adjustedLineTotalCents = (item.lineTotalCents - discountApplied).coerceAtLeast(1)
                
                // Calculate unit price from adjusted line total
                val adjustedUnitPriceCents = if (item.dto.quantity > 0) {
                    (adjustedLineTotalCents / item.dto.quantity).coerceAtLeast(1)
                } else {
                    item.unitPriceCents
                }
                
                AdjustedItem(
                    dto = item.dto,
                    adjustedUnitPriceCents = adjustedUnitPriceCents,
                    adjustedLineTotalCents = adjustedLineTotalCents
                )
            }

            // Ensure exact match by adjusting the last item if needed
            val currentSum = adjustedItems.sumOf { it.adjustedLineTotalCents }
            val difference = targetItemsSumCents - currentSum

            if (difference != 0 && adjustedItems.isNotEmpty()) {
                val lastIndex = adjustedItems.size - 1
                val lastItem = adjustedItems[lastIndex]
                val originalItem = items[lastIndex]
                
                val newLineTotal = (lastItem.adjustedLineTotalCents + difference).coerceIn(
                    1,
                    originalItem.lineTotalCents
                )
                val newUnitPrice = if (lastItem.dto.quantity > 0) {
                    (newLineTotal / lastItem.dto.quantity).coerceAtLeast(1)
                } else {
                    lastItem.adjustedUnitPriceCents
                }
                
                return adjustedItems.dropLast(1) + AdjustedItem(
                    dto = lastItem.dto,
                    adjustedUnitPriceCents = newUnitPrice,
                    adjustedLineTotalCents = newLineTotal
                )
            }

            return adjustedItems
        }
    }
}