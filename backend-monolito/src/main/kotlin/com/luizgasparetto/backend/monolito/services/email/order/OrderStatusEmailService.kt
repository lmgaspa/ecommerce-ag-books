package com.luizgasparetto.backend.monolito.services.email.order

import com.luizgasparetto.backend.monolito.models.order.Order
import org.springframework.stereotype.Service

/**
 * Facade para envio de emails relacionados a mudanças de status do pedido.
 * Delega para classes específicas: OrderPendingEmailSender e OrderFailedEmailSender
 */
@Service
class OrderStatusEmailService(
    private val pendingSender: OrderPendingEmailSender,
    private val failedSender: OrderFailedEmailSender
) {

    /**
     * Envia email para cliente quando pedido é criado (status PENDING/WAITING)
     */
    fun sendPendingEmail(order: Order) {
        pendingSender.send(order)
    }

    /**
     * Envia email para cliente quando pagamento é rejeitado (FAILED/DECLINED)
     */
    fun sendFailedEmail(order: Order, reason: String? = null) {
        failedSender.send(order, reason)
    }
}

