// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/EfiPixSendWebhookController.kt
package com.luizgasparetto.backend.monolito.payments.web

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks/efi/pix-send")
class EfiPixSendWebhookController(
    private val jdbc: NamedParameterJdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun onSendWebhook(@RequestBody payload: Map<String, Any?>): ResponseEntity<Void> {
        val rawIdEnvio = (payload["idEnvio"] ?: payload["id_envio"])?.toString()
        val status = (payload["status"] ?: payload["situacao"])?.toString()?.uppercase()
        if (rawIdEnvio.isNullOrBlank()) return ResponseEntity.ok().build()

        // Compat: aceita "payout-<id>" (legado) e "P<id>" (novo). Tenta também parse direto.
        val orderId = when {
            rawIdEnvio.startsWith("payout-") -> rawIdEnvio.removePrefix("payout-").toLongOrNull()
            rawIdEnvio.startsWith("P")       -> rawIdEnvio.removePrefix("P").toLongOrNull()
            else                             -> rawIdEnvio.toLongOrNull()
        }

        if (orderId == null) {
            log.warn("Webhook Efí: idEnvio sem padrão esperado: {}", rawIdEnvio)
            return ResponseEntity.ok().build()
        }

        when (status) {
            "REALIZADO" -> jdbc.update(
                """UPDATE payment_payouts
                      SET status='CONFIRMED', confirmed_at=NOW(), provider_ref=:ref
                    WHERE order_id=:id""",
                mapOf("id" to orderId, "ref" to rawIdEnvio)
            )
            "NAO_REALIZADO" -> jdbc.update(
                """UPDATE payment_payouts
                      SET status='FAILED', failed_at=NOW(), fail_reason='NAO_REALIZADO', provider_ref=:ref
                    WHERE order_id=:id""",
                mapOf("id" to orderId, "ref" to rawIdEnvio)
            )
            else -> log.info("Webhook Efí PIX-SEND: idEnvio={}, status={}", rawIdEnvio, status)
        }
        return ResponseEntity.ok().build()
    }
}
