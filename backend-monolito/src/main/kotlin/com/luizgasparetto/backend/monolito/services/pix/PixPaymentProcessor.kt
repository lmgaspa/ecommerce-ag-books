package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.PixEmailService
import com.luizgasparetto.backend.monolito.services.order.OrderEventsPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PixPaymentProcessor(
    private val orderRepository: OrderRepository,
    private val emailService: PixEmailService,
    private val events: OrderEventsPublisher
) {
    private val log = LoggerFactory.getLogger(PixPaymentProcessor::class.java)
    private val paidStatuses = setOf("CONCLUIDA","LIQUIDADO","LIQUIDADA","ATIVA-RECEBIDA","COMPLETED","PAID")

    fun isPaidStatus(status: String?): Boolean =
        status != null && paidStatuses.contains(status.uppercase())

    @Transactional
    fun markPaidIfNeededByTxid(txid: String): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return true

        val now = OffsetDateTime.now()

        // Só confirma se estiver WAITING e dentro do TTL
        if (order.status != OrderStatus.WAITING) {
            log.info("POLL: ignorado txid={}, status atual={}", txid, order.status)
            return false
        }
        if (order.reserveExpiresAt != null && now.isAfter(order.reserveExpiresAt)) {
            log.info("POLL: ignorado txid={}, pagamento após TTL", txid)
            return false
        }

        order.paid = true
        order.paidAt = now
        order.status = OrderStatus.CONFIRMED
        orderRepository.save(order)
        log.info("POLL: order {} CONFIRMED (txid={})", order.id, txid)

        runCatching {
            emailService.sendPixClientEmail(order)
            emailService.sendPixAuthorEmail(order)
            order.id?.let { events.publishPaid(it) }
        }.onFailure { e ->
            log.warn("POLL: pós-pagamento com falha: {}", e.message)
        }
        return true
    }
}