// src/main/kotlin/com/luizgasparetto/backend/monolito/services/analytics/BooksDimSink.kt
package com.luizgasparetto.backend.monolito.services.analytics

/**
 * Porta de saída (output port) para enviar as linhas de BooksDim
 * para algum destino externo (ex.: BigQuery).
 *
 * Depois você pode ter:
 *  - BigQueryBooksDimSink : implementa upsert de verdade no BigQuery
 *  - LoggingBooksDimSink  : implementação fake para desenvolvimento
 */
interface BooksDimSink {
    fun upsert(rows: List<BooksDimRow>)
}
