// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/EfiPixSendWebhookController.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.luizgasparetto.backend.monolito.services.PayoutEmailService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/webhooks/efi/pix-send")
class EfiPixSendWebhookController(
    private val jdbc: NamedParameterJdbcTemplate,
    private val payoutEmail: PayoutEmailService
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

        // Carrega dados para e-mail (valor líquido e pix_key do favorecido)
        val row = runCatching {
            jdbc.queryForMap(
                "SELECT amount_net, pix_key FROM payment_payouts WHERE order_id=:id",
                mapOf("id" to orderId)
            )
        }.getOrElse {
            log.error("Falha ao carregar payout p/ e-mail (orderId={}): {}", orderId, it.message)
            emptyMap()
        }

        val amountNet = (row["amount_net"] as? BigDecimal) ?: BigDecimal.ZERO
        val pixKey = row["pix_key"]?.toString().orEmpty()

        when (status) {
            "REALIZADO" -> {
                jdbc.update(
                    """UPDATE payment_payouts
                          SET status='CONFIRMED', confirmed_at=NOW(), provider_ref=:ref
                        WHERE order_id=:id""",
                    mapOf("id" to orderId, "ref" to rawIdEnvio)
                )
                // e-mail de sucesso
                runCatching {
                    payoutEmail.sendPayoutConfirmedEmail(
                        orderId = orderId,
                        amount = amountNet,
                        payeePixKey = pixKey,
                        idEnvio = rawIdEnvio
                    )
                }.onFailure { e ->
                    log.error("Falha ao enviar e-mail de CONFIRMED (orderId={}): {}", orderId, e.message)
                }
            }
            "NAO_REALIZADO" -> {
                jdbc.update(
                    """UPDATE payment_payouts
                          SET status='FAILED', failed_at=NOW(), fail_reason='NAO_REALIZADO', provider_ref=:ref
                        WHERE order_id=:id""",
                    mapOf("id" to orderId, "ref" to rawIdEnvio)
                )
                // e-mail de falha
                runCatching {
                    payoutEmail.sendPayoutFailedEmail(
                        orderId = orderId,
                        amount = amountNet,
                        payeePixKey = pixKey,
                        idEnvio = rawIdEnvio,
                        errorCode = "NAO_REALIZADO",
                        errorMsg = "Efí retornou NAO_REALIZADO para o envio do PIX de repasse."
                    )
                }.onFailure { e ->
                    log.error("Falha ao enviar e-mail de FAILED (orderId={}): {}", orderId, e.message)
                }
            }
            else -> log.info("Webhook Efí PIX-SEND: idEnvio={}, status={}", rawIdEnvio, status)
        }
        return ResponseEntity.ok().build()
    }
}
