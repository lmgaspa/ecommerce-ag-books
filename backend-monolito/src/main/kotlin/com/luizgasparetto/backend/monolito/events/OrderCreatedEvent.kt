package com.luizgasparetto.backend.monolito.events

/**
 * Evento de domínio disparado quando um pedido é criado com sucesso.
 * Usado para acionar envio de email AFTER_COMMIT sem sujar a transação do checkout.
 */
data class OrderCreatedEvent(
    val orderId: Long
)
