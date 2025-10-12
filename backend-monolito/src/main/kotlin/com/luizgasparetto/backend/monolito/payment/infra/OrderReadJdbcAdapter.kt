package com.luizgasparetto.backend.monolito.payment.infra

import com.luizgasparetto.backend.monolito.payment.ports.OrderItemView
import com.luizgasparetto.backend.monolito.payment.ports.OrderReadPort
import com.luizgasparetto.backend.monolito.payment.ports.OrderView
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OrderReadJdbcAdapter(
    private val jdbc: JdbcTemplate
) : OrderReadPort {

    override fun findByOrderRefOrExternal(provider: String, orderRef: String?, externalId: String): OrderView? {
        // 1) tenta pelo orderRef (id do pedido em BIGINT)
        if (!orderRef.isNullOrBlank()) {
            val refSql = "SELECT o.id, o.total FROM orders o WHERE o.id = ?::bigint LIMIT 1"
            jdbc.query(refSql, mapper(), orderRef).firstOrNull()?.let { return it }
        }
        // 2) (opcional) tente por alguma referência externa se você tiver essa tabela
        // Aqui mantemos um fallback neutro para não quebrar:
        return null
    }

    private fun mapper() = org.springframework.jdbc.core.RowMapper { rs, _ ->
        val id = rs.getLong("id")
        OrderView(
            id = id,
            total = rs.getBigDecimal("total"),
            items = loadItems(id)
        )
    }

    private fun loadItems(orderId: Long): List<OrderItemView> {
        val sql = """
      SELECT 
        COALESCE(pba.author_id, 0) AS author_id,
        oi.price  AS unit_price,
        oi.quantity AS quantity
      FROM order_items oi
      LEFT JOIN payment_book_authors pba ON pba.book_id = oi.book_id
      WHERE oi.order_id = ?
    """
        return jdbc.query(sql, { rs, _ ->
            OrderItemView(
                authorId = rs.getLong("author_id"),
                unitPrice = rs.getBigDecimal("unit_price"),
                quantity  = rs.getInt("quantity")
            )
        }, orderId)
    }
}
