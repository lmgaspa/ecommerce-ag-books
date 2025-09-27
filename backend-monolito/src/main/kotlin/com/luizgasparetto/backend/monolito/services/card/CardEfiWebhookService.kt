package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class CardEfiWebhookService(
    private val orderRepository: OrderRepository
) {
    /** Payload mínimo esperado do webhook de CARTÃO da Efí. */
    data class EfiCardWebhookPayload(
        val data: CardData?
    ) {
        data class CardData(
            val charge_id: Any?,   // pode vir número ou string
            val status: String?
        )
    }

    @Transactional
    fun handleWebhook(payload: EfiCardWebhookPayload) {
        val chargeId = payload.data?.charge_id?.toString()?.trim().orEmpty()
        if (chargeId.isEmpty()) return

        val newStatus = OrderStatus.fromEfi(payload.data?.status)
        val order = orderRepository.findByChargeId(chargeId) ?: return

        // Não regredir estados finais a menos que seja melhoria (pago-like)
        if (order.status.isFinal() && !newStatus.isPaidLike()) return

        val wasPaidLike = order.status.isPaidLike()
        order.status = newStatus

        when {
            newStatus.isPaidLike() -> {
                order.paid = true
                if (!wasPaidLike) order.paidAt = OffsetDateTime.now()
            }
            newStatus in arrayOf(OrderStatus.REFUNDED, OrderStatus.PARTIALLY_REFUNDED) -> {
                // já houve pagamento; mantemos paid=true e usamos o status para tratativas de estorno
                order.paid = true
            }
            newStatus in arrayOf(OrderStatus.CANCELED, OrderStatus.DECLINED, OrderStatus.UNPAID, OrderStatus.EXPIRED) -> {
                order.paid = false
            }
        }

        orderRepository.save(order)
    }
}
