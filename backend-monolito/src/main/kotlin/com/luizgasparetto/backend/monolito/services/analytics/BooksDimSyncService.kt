// src/main/kotlin/com/luizgasparetto/backend/monolito/services/analytics/BooksDimSyncService.kt
package com.luizgasparetto.backend.monolito.services.analytics

import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Serviço responsável por ler todos os livros do Postgres (books)
 * e gerar as linhas da dimensão books_dim que vão alimentar o BigQuery.
 *
 * Ele não sabe nada de BigQuery; apenas delega para a BooksDimSink.
 * Assim, você troca a implementação da sink (logging -> BigQuery) sem
 * precisar mexer aqui (OCP).
 */
@Service
class BooksDimSyncService(
    private val bookRepository: BookRepository,
    private val sink: BooksDimSink
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun syncAllBooks() {
        log.info("Iniciando sync de books -> books_dim")

        val books = bookRepository.findAll()

        if (books.isEmpty()) {
            log.info("Nenhum livro encontrado no banco. Nada para sincronizar.")
            sink.upsert(emptyList())
            return
        }

        val rows = books.map { book ->
            // Ajuste aqui os campos conforme seu modelo real de Book
            val authorId = book.authorRef?.id   // se o campo tiver outro nome, adapta

            BooksDimRow(
                authorId = authorId,
                bookId = book.id,
                title = book.title
            )
        }

        log.info("Geradas {} linhas para books_dim; enviando para BooksDimSink...", rows.size)
        sink.upsert(rows)
        log.info("Sync de books_dim concluído.")
    }
}
