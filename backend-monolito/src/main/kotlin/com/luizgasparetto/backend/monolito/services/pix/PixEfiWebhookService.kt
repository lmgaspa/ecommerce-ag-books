package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PixEfiWebhookService(
        private val orderRepository: OrderRepository,
        private val processor: PixPaymentProcessor
) {
    /**
     * Payload típico do webhook de PIX pode vir como: { "pix": [ { "txid": "...", "status":
     * "paid|confirmed|waiting|refunded|expired|canceled|..." } ] }
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

            // 1) Se for status de PAGAMENTO, delegamos para o Processor Central
            // Ele já faz: validação status/TTL, marca pago, emails, SSE e gatilho de repasse.
            if (processor.isPaidStatus(evt.status)) {
                processor.markPaidIfNeededByTxid(txid)
                return@forEach
            }

            // 2) Outros status (ex: devolvida, cancelada) tratamos apenas atualizando o banco
            val newStatus = mapPixStatusToOrderStatus(evt.status)
            val order = orderRepository.findByTxid(txid) ?: return@forEach

            if (order.status.isFinal()) return@forEach

            order.status = newStatus

            if (newStatus == OrderStatus.REFUNDED) {
                // se foi devolvida, tecnicamente já foi paga antes, mas agora estornou
                // mantemos paid=true? Depende da regra. Geralmente refund = paid=true
                // status=REFUNDED
            } else if (newStatus == OrderStatus.CANCELED || newStatus == OrderStatus.EXPIRED) {
                order.paid = false
            }

            orderRepository.save(order)
        }
    }

    /** Converte status recebidos no webhook de PIX para o enum unificado. */
    private fun mapPixStatusToOrderStatus(status: String?): OrderStatus =
            when (status?.lowercase()?.trim()) {
                // inglês (alguns PSPs)
                "paid" -> OrderStatus.PAID
                "confirmed" -> OrderStatus.CONFIRMED
                "waiting" -> OrderStatus.WAITING
                "refunded" -> OrderStatus.REFUNDED
                "canceled" -> OrderStatus.CANCELED
                "expired" -> OrderStatus.EXPIRED

                // português (variações frequentes)
                "concluida" -> OrderStatus.PAID
                "removida_pelo_usuario_recebedor" -> OrderStatus.CANCELED
                "removida_pelo_psp" -> OrderStatus.CANCELED
                "devolvida" -> OrderStatus.REFUNDED

                // default: tenta mapear pelo fromEfi (caso venha algo como "approved", etc.)
                else -> OrderStatus.fromEfi(status)
            }
}
