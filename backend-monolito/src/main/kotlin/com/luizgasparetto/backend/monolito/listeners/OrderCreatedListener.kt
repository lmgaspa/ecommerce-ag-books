package com.luizgasparetto.backend.monolito.events

import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.email.order.OrderStatusEmailService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Listener para OrderCreatedEvent.
 *
 * Responsabilidade:
 * - Rodar AFTER_COMMIT
 * - Recarregar o pedido do banco (com itens) e disparar o email de "pedido criado"
 * - Garantir que qualquer falha aqui NÃO afete a transação principal
 */
@Component
class OrderCreatedListener(
    private val orderRepository: OrderRepository,
    private val orderStatusEmailService: OrderStatusEmailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderCreated(event: OrderCreatedEvent) {
        val order = orderRepository.findWithItemsById(event.orderId)
        if (order == null) {
            log.warn("OrderCreatedEvent: pedido {} não encontrado após commit", event.orderId)
            return
        }

        runCatching {
            orderStatusEmailService.sendPendingEmail(order)
        }.onFailure { e ->
            log.warn(
                "OrderCreatedEvent: falha ao enviar email de pedido criado (orderId={}): {}",
                order.id,
                e.message
            )
        }
    }
}
