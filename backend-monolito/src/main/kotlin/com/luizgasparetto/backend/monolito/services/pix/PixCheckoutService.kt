package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.dto.pix.PixCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.pix.PixCheckoutResponse
import com.luizgasparetto.backend.monolito.models.coupon.OrderCoupon
import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderItem
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderCouponRepository
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.coupon.CouponService
import com.luizgasparetto.backend.monolito.services.email.order.OrderStatusEmailService
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PixCheckoutService(
        private val orderRepository: OrderRepository,
        private val bookService: BookService,
        private val pixWatcher: PixWatcher,
        private val pixService: PixService,
        private val couponService: CouponService,
        private val orderCouponRepository: OrderCouponRepository,
        private val orderStatusEmailService: OrderStatusEmailService,
        @Value("\${efi.pix.chave}") private val chavePix: String,
        @Value("\${checkout.reserve.ttl-seconds:300}") private val reserveTtlSeconds: Long
) {
        private val log = LoggerFactory.getLogger(PixCheckoutService::class.java)

        fun processCheckout(request: PixCheckoutRequest): PixCheckoutResponse {
                // 0) valida disponibilidade (checagem rápida)
                request.cartItems.forEach { item ->
                        bookService.validateStock(item.id, item.quantity)
                }

                // 0.1) calcula total original e processa desconto
                val originalTotal = calculateOriginalTotal(request)
                val (finalTotal, discountAmount) = processDiscount(request, originalTotal)

                // Log detalhado para debug
                log.info("📦 CHECKOUT PIX - Dados recebidos:")
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
                        throw IllegalArgumentException(
                                "Valor do pedido muito baixo. Valor mínimo: R$ 0,01"
                        )
                }

                val txid = UUID.randomUUID().toString().replace("-", "").take(35)

                // 1) cria pedido + itens (TX)
                val order =
                        createOrderTx(request, finalTotal, discountAmount, request.couponCode, txid)

                // 2) reserva estoque + define TTL (TX)
                reserveItemsTx(order, reserveTtlSeconds)

                // 3) cria cobrança Pix via serviço centralizado; se falhar, libera reserva
                val cob =
                        try {
                                pixService.criarPixCobranca(
                                        finalTotal,
                                        chavePix,
                                        "Pedido $txid",
                                        txid
                                )
                        } catch (e: Exception) {
                                log.error(
                                        "Falha ao criar QR na Efí, liberando reserva. orderId={}, txid={}, err={}",
                                        order.id,
                                        txid,
                                        e.message,
                                        e
                                )
                                releaseReservationTx(order.id!!)
                                throw e
                        }

                // 4) grava QR no pedido (TX)
                updateOrderWithQrTx(order.id!!, cob.pixCopiaECola, cob.imagemQrcodeBase64)

                // 5) inicia watcher até o TTL
                val expireAtInstant =
                        requireNotNull(order.reserveExpiresAt) {
                                        "reserveExpiresAt nulo após reserva"
                                }
                                .toInstant()
                pixWatcher.watch(txid, expireAtInstant)

                // 6) Sucesso total: Retorna resposta com QR Code
                // Email de "pendente" REMOVIDO por solicitação do usuário.
                // O cliente receberá email apenas quando o pagamento for confirmado (via
                // Poller/Webhook).

                log.info(
                        "CHECKOUT PIX OK: orderId={}, txid={}, ttl={}s, expiraEm={}, qrLen={}, imgLen={}",
                        order.id,
                        txid,
                        reserveTtlSeconds,
                        order.reserveExpiresAt,
                        cob.pixCopiaECola.length,
                        cob.imagemQrcodeBase64.length
                )

                return PixCheckoutResponse(
                        qrCode = cob.pixCopiaECola,
                        qrCodeBase64 = cob.imagemQrcodeBase64,
                        message = "Pedido gerado com sucesso",
                        orderId = order.id.toString(),
                        txid = txid,
                        reserveExpiresAt = order.reserveExpiresAt?.toString(),
                        ttlSeconds = reserveTtlSeconds,
                        warningAt = 10, // Avisar quando faltar 10 segundos
                        securityWarningAt =
                                10 // INVALIDAR quando faltar 10 segundos (segurança máxima)
                )
        }

        // ------------------- privados -------------------

        private fun calculateOriginalTotal(request: PixCheckoutRequest): BigDecimal {
                val totalBooks =
                        request.cartItems.sumOf {
                                it.price.toBigDecimal() * BigDecimal(it.quantity)
                        }
                return totalBooks + request.shipping.toBigDecimal()
        }

        private fun processDiscount(
                request: PixCheckoutRequest,
                originalTotal: BigDecimal
        ): Pair<BigDecimal, BigDecimal> {
                // Se não há cupom, não há desconto
                if (request.couponCode.isNullOrBlank()) {
                        return Pair(originalTotal, BigDecimal.ZERO)
                }

                // Validar cupom primeiro
                // Converter PixCartItemDto para CardCartItemDto (mesma estrutura)
                val cardCartItems =
                        request.cartItems.map { pixItem ->
                                com.luizgasparetto.backend.monolito.dto.card.CardCartItemDto(
                                        id = pixItem.id,
                                        title = pixItem.title,
                                        price = pixItem.price,
                                        quantity = pixItem.quantity,
                                        imageUrl = pixItem.imageUrl
                                )
                        }

                val couponValidation =
                        couponService.validateCoupon(
                                CouponService.CouponValidationRequest(
                                        code = request.couponCode,
                                        orderTotal = originalTotal,
                                        userEmail = request.email,
                                        cartItems = cardCartItems
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

                // SEGURANÇA: O backend é a fonte da verdade para o desconto.
                // Ignoramos completamente o request.discount do frontend para evitar manipulação.
                // O frontend deve enviar apenas o couponCode, e o backend recalcula tudo do zero.
                val calculatedDiscount = couponValidation.discountAmount

                // Garantir que o desconto não seja maior que o total (deixando pelo menos R$ 0,01)
                val maxAllowedDiscount = originalTotal - BigDecimal("0.01")
                val limitedDiscount = minOf(calculatedDiscount, maxAllowedDiscount)

                val finalTotal = originalTotal - limitedDiscount

                log.info("💳 PROCESSAMENTO DESCONTO PIX:")
                log.info("  - Desconto frontend (IGNORADO por segurança): {}", request.discount)
                log.info("  - Desconto calculado pelo backend: {}", calculatedDiscount)
                log.info("  - Desconto final aplicado: {}", limitedDiscount)
                log.info("  - Total final: {}", finalTotal)

                return Pair(finalTotal, limitedDiscount)
        }

        @Transactional
        fun createOrderTx(
                request: PixCheckoutRequest,
                totalAmount: BigDecimal,
                discountAmount: BigDecimal,
                couponCode: String?,
                txid: String
        ): Order {
                val order =
                        Order(
                                firstName = request.firstName,
                                lastName = request.lastName,
                                email = request.email,
                                cpf = request.cpf,
                                number = request.number,
                                complement = request.complement,
                                district = request.district,
                                address = request.address,
                                city = request.city,
                                state = request.state,
                                cep = request.cep,
                                phone = request.phone,
                                note = request.note,
                                total = totalAmount,
                                shipping = request.shipping.toBigDecimal(),
                                couponCode = couponCode,
                                discountAmount =
                                        if (discountAmount > BigDecimal.ZERO) discountAmount
                                        else null,
                                paid = false,
                                txid = txid,
                                items = mutableListOf(),
                                status = OrderStatus.NEW,
                                paymentMethod = "pix"
                        )

                order.items =
                        request.cartItems
                                .map {
                                        OrderItem(
                                                bookId = it.id,
                                                title = it.title,
                                                quantity = it.quantity,
                                                price = it.price.toBigDecimal(),
                                                imageUrl = bookService.getImageUrl(it.id),
                                                order = order
                                        )
                                }
                                .toMutableList()

                val saved = orderRepository.save(order)

                // Salvar cupom aplicado se houver
                if (couponCode != null && discountAmount > BigDecimal.ZERO) {
                        val coupon = couponService.getCouponByCode(couponCode)
                        if (coupon != null) {
                                val orderCoupon =
                                        OrderCoupon(
                                                order = saved,
                                                coupon = coupon,
                                                originalTotal = calculateOriginalTotal(request),
                                                discountAmount = discountAmount,
                                                finalTotal = totalAmount
                                        )
                                orderCouponRepository.save(orderCoupon)
                                log.info(
                                        "Cupom aplicado: orderId={}, couponCode={}, discount={}",
                                        saved.id,
                                        couponCode,
                                        discountAmount
                                )
                        }
                }

                log.info("TX1: order salvo id={}, txid={}", saved.id, txid)
                return saved
        }

        /** Reserva todos os itens do pedido e define TTL da reserva. */
        @Transactional
        fun reserveItemsTx(order: Order, ttlSeconds: Long) {
                order.items.forEach { item ->
                        bookService.reserveOrThrow(item.bookId, item.quantity)
                }
                order.status = OrderStatus.WAITING
                order.reserveExpiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds)
                orderRepository.save(order)
                log.info(
                        "RESERVA: orderId={} ttl={}s expiraEm={}",
                        order.id,
                        ttlSeconds,
                        order.reserveExpiresAt
                )
        }

        /** Libera a reserva de um pedido (usado se falhar emissão de QR). */
        @Transactional
        fun releaseReservationTx(orderId: Long) {
                val order =
                        orderRepository.findById(orderId).orElseThrow {
                                IllegalStateException("Order $orderId não encontrado")
                        }
                if (order.status == OrderStatus.WAITING && !order.paid) {
                        order.items.forEach { item ->
                                bookService.release(item.bookId, item.quantity)
                        }
                        order.status = OrderStatus.EXPIRED
                        order.reserveExpiresAt = null
                        orderRepository.save(order)
                        log.info("RESERVA LIBERADA: orderId={}", orderId)
                }
        }

        @Transactional
        fun updateOrderWithQrTx(orderId: Long, qrCode: String, qrB64: String) {
                val order =
                        orderRepository.findById(orderId).orElseThrow {
                                IllegalStateException("Order $orderId não encontrado")
                        }
                order.qrCode = qrCode
                order.qrCodeBase64 = qrB64
                orderRepository.save(order)
                log.info("TX2: QR gravado. orderId={}", orderId)
        }
}
