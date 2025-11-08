package com.seuapp.ga4.service

import com.google.cloud.bigquery.*
import com.seuapp.ga4.dto.FunnelDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class Ga4LiveService(
    private val bigQuery: BigQuery,
    @Value("\${ga4.bq.project-id}") private val projectId: String,
    @Value("\${ga4.bq.dataset-id}") private val datasetId: String,
    @Value("\${ga4.bq.max-bytes-billed:1000000000000}") private val maxBytesBilled: Long
) : Ga4Service {

    private val iso = DateTimeFormatter.BASIC_ISO_DATE
    private val tablePath get() = "`$projectId.$datasetId.events_*`"

    override fun funnel(from: LocalDate, to: LocalDate, authorId: UUID): FunnelDTO {
        val start = from.format(iso)
        val end   = to.format(iso)

        val sql = """
            WITH base AS (
              SELECT e.event_name
              FROM $tablePath e, UNNEST(e.event_params) p
              WHERE _TABLE_SUFFIX BETWEEN @start_day AND @end_day
                AND p.key = 'author_id'
                AND JSON_VALUE(p.value, '$.string_value') = @auth
            )
            SELECT
              SUM(event_name = 'view_item')      AS view_item,
              SUM(event_name = 'add_to_cart')    AS add_to_cart,
              SUM(event_name = 'begin_checkout') AS begin_checkout,
              SUM(event_name = 'purchase')       AS purchase
            FROM base
        """.trimIndent()

        val q = QueryJobConfiguration.newBuilder(sql)
            .addNamedParameter("start_day", QueryParameterValue.string(start))
            .addNamedParameter("end_day",   QueryParameterValue.string(end))
            .addNamedParameter("auth",      QueryParameterValue.string(authorId.toString()))
            .setMaximumBytesBilled(maxBytesBilled)
            .build()

        try {
            val rows = bigQuery.query(q).iterateAll().iterator()
            if (!rows.hasNext()) return FunnelDTO(0,0,0,0)
            val r = rows.next()
            return FunnelDTO(
                r.longOrZero("view_item"),
                r.longOrZero("add_to_cart"),
                r.longOrZero("begin_checkout"),
                r.longOrZero("purchase")
            )
        } catch (ex: BigQueryException) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "GA4/BigQuery indisponível: ${ex.message}", ex
            )
        } catch (ex: Exception) {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Falha ao consultar GA4/BigQuery.", ex
            )
        }
    }

    private fun FieldValueList.longOrZero(field: String): Long =
        get(field)?.let { if (it.isNull) 0L else it.longValue } ?: 0L
}
