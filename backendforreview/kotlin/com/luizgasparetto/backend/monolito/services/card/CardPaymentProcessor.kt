package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.CardEmailService
import com.luizgasparetto.backend.monolito.services.order.OrderEventsPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class CardPaymentProcessor(
    private val orderRepository: OrderRepository,
    private val emailService: CardEmailService,
    private val events: OrderEventsPublisher
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
        return true
    }
}
