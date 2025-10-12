package com.luizgasparetto.backend.monolito.payment.infra

import com.luizgasparetto.backend.monolito.payment.ports.PaymentAuthorAccountPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PaymentAuthorAccountJdbcAdapter(
    private val jdbc: JdbcTemplate
) : PaymentAuthorAccountPort {

    override fun findPixKeyByAuthorId(authorId: Long): String? {
        val sql = "SELECT pix_key FROM payment_author_accounts WHERE author_id = ? LIMIT 1"
        return jdbc.query(sql, { rs, _ -> rs.getString("pix_key") }, authorId).firstOrNull()
    }
}
