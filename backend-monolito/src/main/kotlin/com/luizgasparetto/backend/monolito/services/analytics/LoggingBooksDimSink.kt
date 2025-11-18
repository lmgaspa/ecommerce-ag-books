// src/main/kotlin/com/luizgasparetto/backend/monolito/services/analytics/LoggingBooksDimSink.kt
package com.luizgasparetto.backend.monolito.services.analytics

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Implementação inicial que só faz log das linhas geradas.
 *
 * É segura pra rodar em qualquer ambiente, inclusive Heroku,
 * e prepara o terreno pra depois você plugar um BigQueryBooksDimSink
 * sem mexer no BooksDimSyncService (OCP).
 */
@Component
class LoggingBooksDimSink : BooksDimSink {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun upsert(rows: List<BooksDimRow>) {
        if (rows.isEmpty()) {
            log.info("BooksDimSyncService: nenhum livro para sincronizar (0 linhas)")
            return
        }

        log.info("BooksDimSyncService: gerando {} linhas para books_dim (stub, ainda sem BigQuery)", rows.size)

        // Se quiser depurar algum exemplo:
        rows.take(5).forEach { row ->
            log.debug("books_dim row: authorId={}, bookId={}, title={}", row.authorId, row.bookId, row.title)
        }
    }
}
