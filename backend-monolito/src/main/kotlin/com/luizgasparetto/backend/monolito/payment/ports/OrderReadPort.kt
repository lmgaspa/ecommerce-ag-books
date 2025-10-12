package com.luizgasparetto.backend.monolito.payment.ports

import java.math.BigDecimal

// OrderReadPort.kt
/* --------- PORTS / DTOs (tipados em Long) --------- */

interface OrderReadPort {
    fun findByOrderRefOrExternal(provider: String, orderRef: String?, externalId: String): OrderView?
}

data class OrderView(
    val id: Long,
    val total: BigDecimal,
    val items: List<OrderItemView>
)

data class OrderItemView(
    val authorId: Long,
    val unitPrice: BigDecimal,
    val quantity: Int
)

interface PaymentAuthorAccountPort {
    fun findPixKeyByAuthorId(authorId: Long): String?
}
