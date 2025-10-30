// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/PaymentRawEventService.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class PaymentRawEventService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun saveRaw(
        provider: String,
        eventType: String,
        externalId: String,
        orderRef: String?,
        payload: Map<String, Any?>
    ) {
        val json = mapper.writeValueAsString(payload + mapOf("orderRef" to orderRef))

        // Descobre colunas existentes para suportar esquemas diferentes (legado vs novo)
        val hasPayloadJson = hasCol("payment_webhook_events", "payload_json")
        val hasPayload     = hasCol("payment_webhook_events", "payload")
        val hasOrderRef    = hasCol("payment_webhook_events", "order_ref")
        val hasReceivedAt  = hasCol("payment_webhook_events", "received_at")

        // Monta SQL dinamicamente
        val cols = mutableListOf("provider","external_id","event_type")
        val vals = mutableListOf(":p",":e",":t")

        if (hasPayloadJson) { cols += "payload_json"; vals += "cast(:j as jsonb)" }
        if (hasPayload)     { cols += "payload";      vals += "cast(:j as jsonb)" } // mesma carga JSON
        if (hasOrderRef)    { cols += "order_ref";    vals += ":o" }
        if (hasReceivedAt)  { cols += "received_at";  vals += "NOW()" }

        val sql = """
            INSERT INTO payment_webhook_events(${cols.joinToString(",")})
            VALUES (${vals.joinToString(",")})
            ON CONFLICT (provider, external_id, event_type) DO NOTHING
        """.trimIndent()

        val params = mapOf(
            "p" to provider,
            "e" to externalId,
            "t" to eventType,
            "j" to json,
            "o" to orderRef
        )

        runCatching { jdbc.update(sql, params) }.onFailure { ex ->
            log.error(
                "RAW save fail provider={} eventType={} external={} err={}",
                provider, eventType, externalId, ex.message, ex
            )
        }
    }

    private fun hasCol(table: String, column: String): Boolean =
        jdbc.queryForList(
            """
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND table_name = :t AND column_name = :c
            LIMIT 1
            """.trimIndent(),
            mapOf("t" to table, "c" to column),
            Int::class.java
        ).isNotEmpty()
}
