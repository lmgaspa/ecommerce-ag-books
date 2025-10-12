package com.luizgasparetto.backend.monolito.payments.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service

@Service
class PaymentRawEventService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val mapper: ObjectMapper
) {
    fun saveRaw(
        provider: String,
        eventType: String,
        externalId: String,
        orderRef: String?,
        payload: Map<String, Any?>
    ) {
        val json = mapper.writeValueAsString(
            payload + mapOf("orderRef" to orderRef)
        )
        jdbc.update(
            """
            INSERT INTO payment_webhook_events(provider, external_id, event_type, payload_json)
            VALUES (:p, :e, :t, cast(:j as jsonb))
            ON CONFLICT (provider, external_id, event_type) DO NOTHING
            """.trimIndent(),
            mapOf("p" to provider, "e" to externalId, "t" to eventType, "j" to json)
        )
    }
}