package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PixEfiWebhookService(
    private val orderRepository: OrderRepository
) {
    /**
     * Payload típico do webhook de PIX pode vir como:
     * { "pix": [ { "txid": "...", "status": "paid|confirmed|waiting|refunded|expired|canceled|..." } ] }
     */
    data class PixEvent(val txid: String?, val status: String?)
    data class PixWebhookPayload(val pix: List<PixEvent>?)

    @Transactional
    fun handleWebhook(payload: PixWebhookPayload) {
        val events = payload.pix.orEmpty()
        if (events.isEmpty()) return

        events.forEach { evt ->
            val txid = evt.txid?.trim().orEmpty()
            if (txid.isEmpty()) return@forEach

            val newStatus = mapPixStatusToOrderStatus(evt.status)
            val order = orderRepository.findByTxid(txid) ?: return@forEach

            if (order.status.isFinal() && !newStatus.isPaidLike()) return@forEach

            val wasPaidLike = order.status.isPaidLike()
            order.status = newStatus

            when {
                newStatus.isPaidLike() -> {
                    order.paid = true
                    if (!wasPaidLike) order.paidAt = OffsetDateTime.now()
                }
                newStatus == OrderStatus.REFUNDED -> {
                    order.paid = true // já houve pagamento; status indica estorno
                }
                newStatus in arrayOf(OrderStatus.CANCELED, OrderStatus.EXPIRED) -> {
                    order.paid = false
                }
            }

            orderRepository.save(order)
        }
    }

    /** Converte status recebidos no webhook de PIX para o enum unificado. */
    private fun mapPixStatusToOrderStatus(status: String?): OrderStatus =
        when (status?.lowercase()?.trim()) {
            // inglês (alguns PSPs)
            "paid"        -> OrderStatus.PAID
            "confirmed"   -> OrderStatus.CONFIRMED
            "waiting"     -> OrderStatus.WAITING
            "refunded"    -> OrderStatus.REFUNDED
            "canceled"    -> OrderStatus.CANCELED
            "expired"     -> OrderStatus.EXPIRED

            // português (variações frequentes)
            "concluida"                       -> OrderStatus.PAID
            "removida_pelo_usuario_recebedor" -> OrderStatus.CANCELED
            "removida_pelo_psp"               -> OrderStatus.CANCELED
            "devolvida"                       -> OrderStatus.REFUNDED

            // default: tenta mapear pelo fromEfi (caso venha algo como "approved", etc.)
            else -> OrderStatus.fromEfi(status)
        }
}