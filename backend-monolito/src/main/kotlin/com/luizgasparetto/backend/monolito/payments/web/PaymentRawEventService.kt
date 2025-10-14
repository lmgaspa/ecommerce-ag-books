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
        externalId: String?,
        orderRef: String?,
        payload: Map<String, Any?>
    ) {
        val jsonTxt = mapper.writeValueAsString(payload + mapOf("orderRef" to orderRef))
        val external = externalId?.takeIf { it.isNotBlank() } ?: extractExternalId(payload).orEmpty()

        // Descobre colunas do jeito mais robusto possível
        val hasPayloadJson = tableHasColumn("payment_webhook_events", "payload_json")
        val hasPayloadTxt  = tableHasColumn("payment_webhook_events", "payload")        // legado (TEXT NOT NULL)
        val hasOrderRef    = tableHasColumn("payment_webhook_events", "order_ref")
        val hasChargeId    = tableHasColumn("payment_webhook_events", "charge_id")
        val hasTxid        = tableHasColumn("payment_webhook_events", "txid")

        val cols = mutableListOf("provider", "external_id", "event_type")
        val vals = mutableListOf(":p", ":e", ":t")

        if (hasPayloadJson) { cols += "payload_json"; vals += "cast(:j as jsonb)" }
        if (hasPayloadTxt)  { cols += "payload";      vals += ":j" }                  // preenche também o legado
        if (hasOrderRef)    { cols += "order_ref";    vals += ":o" }
        if (hasChargeId)    { cols += "charge_id";    vals += ":c" }
        if (hasTxid)        { cols += "txid";         vals += ":x" }

        val sql = buildString {
            append("INSERT INTO payment_webhook_events(")
            append(cols.joinToString(","))
            append(") VALUES (")
            append(vals.joinToString(","))
            append(") ON CONFLICT (provider, external_id, event_type) DO NOTHING")
        }

        val params = mapOf(
            "p" to provider,
            "e" to external,
            "t" to eventType,
            "j" to jsonTxt,
            "o" to orderRef,
            "c" to extractChargeId(payload),
            "x" to extractTxid(payload)
        )

        try {
            jdbc.update(sql, params)
        } catch (ex: Exception) {
            // Não derrube o fluxo do webhook por causa de auditoria
            log.error("RAW save fail provider={} eventType={} external={} err={}", provider, eventType, external, ex.message, ex)
        }
    }

    // ===== helpers =====

    private fun tableHasColumn(table: String, column: String): Boolean =
        jdbc.queryForList(
            """
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND table_name = :t
               AND column_name = :c
            LIMIT 1
            """.trimIndent(),
            mapOf("t" to table, "c" to column),
            Int::class.java
        ).isNotEmpty()

    /** tenta achar um txid "em qualquer lugar" do payload */
    private fun extractTxid(payload: Map<String, Any?>): String? {
        fun any(obj: Any?): String? = when (obj) {
            is Map<*, *> -> obj["txid"]?.toString()?.takeIf { it.isNotBlank() }
                ?: obj.values.asSequence().mapNotNull { any(it) }.firstOrNull()
            is Iterable<*> -> obj.asSequence().mapNotNull { any(it) }.firstOrNull()
            else -> null
        }
        return any(payload)
    }

    /** tenta achar charge_id em estruturas comuns */
    private fun extractChargeId(payload: Map<String, Any?>): String? {
        fun get(m: Map<*, *>, vararg path: String): String? {
            var cur: Any? = m
            for (p in path) {
                cur = (cur as? Map<*, *>)?.get(p) ?: return null
            }
            return cur?.toString()
        }
        return get(payload, "charge_id")
            ?: get(payload, "payment", "charge_id")
            ?: get(payload, "data", "charge_id")
            ?: get(payload, "charge", "id")
            ?: get(payload, "data", "charge", "id")
    }

    /** externalId padrão: txid → charge_id → vazio */
    private fun extractExternalId(payload: Map<String, Any?>): String? =
        extractTxid(payload) ?: extractChargeId(payload)
}
