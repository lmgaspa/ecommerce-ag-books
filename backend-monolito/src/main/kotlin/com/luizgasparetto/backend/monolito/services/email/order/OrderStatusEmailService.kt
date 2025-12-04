package com.luizgasparetto.backend.monolito.services.email.order

import com.luizgasparetto.backend.monolito.models.order.Order
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Facade para envio de emails relacionados a mudanças de status do pedido. Delega para classes
 * específicas: OrderPendingEmailSender e OrderFailedEmailSender
 */
@Service
class OrderStatusEmailService(
        private val pendingSender: OrderPendingEmailSender,
        private val failedSender: OrderFailedEmailSender
) {

    /**
     * Envia email para cliente quando pedido é criado (status PENDING/WAITING)
     *
     * Usa REQUIRES_NEW para que:
     * - o registro em order_email seja persistido em transação própria
     * - qualquer erro aqui não interfira na transação principal do checkout
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun sendPendingEmail(order: Order) {
        pendingSender.send(order)
    }

    /** Envia email para cliente quando pagamento é rejeitado (FAILED/DECLINED) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun sendFailedEmail(order: Order, reason: String? = null) {
        failedSender.send(order, reason)
    }
}
