package com.luizgasparetto.backend.monolito.payment.adapters

import com.luizgasparetto.backend.monolito.payment.ports.AuthorDirectory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class AuthorDirectoryAdapter(private val jdbc: JdbcTemplate) : AuthorDirectory {
    override fun resolvePixKey(authorId: UUID): String? {
        val existing = jdbc.queryForList(
            "SELECT pix_key FROM authors WHERE id = ? AND pix_key IS NOT NULL LIMIT 1",
            String::class.java, authorId
        )
        if (existing.isNotEmpty()) return existing.first()
        val fallback = jdbc.queryForList(
            "SELECT pix_key FROM payment_author_accounts WHERE author_id = ? LIMIT 1",
            String::class.java, authorId
        )
        return fallback.firstOrNull()
    }
}