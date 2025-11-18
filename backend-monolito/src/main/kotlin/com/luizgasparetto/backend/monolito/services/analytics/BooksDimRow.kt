// src/main/kotlin/com/luizgasparetto/backend/monolito/services/analytics/BooksDimRow.kt
package com.luizgasparetto.backend.monolito.services.analytics

/**
 * Linha que representa um livro na dimensão de produtos (books_dim) do BigQuery.
 *
 * Mantemos só o essencial (YAGNI):
 * - authorId: quem é o autor dono do livro (chave de ligação backend <-> dashboard)
 * - bookId:   id técnico do livro (tem que bater com item_id no GA4/BigQuery)
 * - title:    título oficial do livro (o bonito, vindo do Postgres)
 */
data class BooksDimRow(
    val authorId: Long?,   // pode estar nulo enquanto você termina as migrações
    val bookId: String,
    val title: String
)
