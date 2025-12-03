package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.email.CardEmailService
import com.luizgasparetto.backend.monolito.services.order.OrderEventsPublisher
import com.luizgasparetto.backend.monolito.services.payout.card.PayoutCardEmailService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class CardPaymentProcessor(
    private val orderRepository: OrderRepository,
    private val emailService: CardEmailService,
    private val events: OrderEventsPublisher,
    private val payoutCardEmailService: PayoutCardEmailService
) {
    private val log = LoggerFactory.getLogger(CardPaymentProcessor::class.java)
    private val cardPaid = setOf("PAID","APPROVED","CAPTURED","CONFIRMED")

    fun isCardPaidStatus(status: String?): Boolean =
        status != null && cardPaid.contains(status.uppercase())

    @Transactional
    fun markPaidIfNeededByChargeId(chargeId: String): Boolean {
        val order = orderRepository.findWithItemsByChargeId(chargeId) ?: return false
        if (order.paid) return true

        val now = OffsetDateTime.now()
        if (order.status != OrderStatus.WAITING) {
            log.info("CONFIRM CARD: ignorado chargeId={}, status atual={}", chargeId, order.status); return false
        }
        if (order.reserveExpiresAt != null && now.isAfter(order.reserveExpiresAt)) {
            log.info("CONFIRM CARD: ignorado (após TTL) chargeId={}", chargeId); return false
        }

        order.paid = true
        order.paidAt = now
        order.status = OrderStatus.CONFIRMED
        orderRepository.save(order)
        log.info("CONFIRM CARD: order {} CONFIRMED (chargeId={})", order.id, chargeId)

        runCatching {
            emailService.sendCardClientEmail(order)
            emailService.sendCardAuthorEmail(order)
            order.id?.let { events.publishPaid(it) }
        }.onFailure { e -> log.warn("CONFIRM CARD: pós-pagamento falhou: {}", e.message) }

        // 🔔 EMAIL DE REPASSE AGENDADO (CARTÃO): informa sobre repasse D+31
        // Enviado imediatamente após confirmação do pagamento, independente do webhook
        order.id?.let { orderId ->
            runCatching {
                payoutCardEmailService.sendPayoutScheduledEmail(
                    orderId = orderId,
                    amount = order.total,
                    payeePixKey = null, // Será resolvido pelo PaymentTriggerService quando o repasse for processado
                    idEnvio = "C$orderId",
                    extraNote = "Repasse programado para 32 dias (política Efí Bank)"
                )
                log.info("CARD PAYOUT EMAIL: Enviado email de repasse agendado para order #{} (D+32)", orderId)
            }.onFailure { e ->
                log.error("CONFIRM CARD: falha ao enviar email de repasse agendado (orderId={}, chargeId={}): {}", orderId, chargeId, e.message, e)
            }
        }

        return true
    }
}