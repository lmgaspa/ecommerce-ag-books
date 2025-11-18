// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/EfiPixSendWebhookController.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.luizgasparetto.backend.monolito.services.payout.pix.PayoutPixEmailService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import java.math.BigDecimal

@RestController
@RequestMapping("${ApiRoutes.API_V1}/webhooks/payout")
class EfiPixSendWebhookController(
    private val jdbc: NamedParameterJdbcTemplate,
    private val mail: PayoutPixEmailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/pix")
    fun onSendWebhook(@RequestBody payload: Map<String, Any?>): ResponseEntity<Void> {
        // exemplos de campos possíveis: idEnvio, status (REALIZADO|NAO_REALIZADO), endToEndId, txid, valor
        val rawIdEnvio = (payload["idEnvio"] ?: payload["id_envio"])?.toString()
        val status = (payload["status"] ?: payload["situacao"])?.toString()?.uppercase()
        val endToEndId = payload["endToEndId"]?.toString()
        val txid = payload["txid"]?.toString()

        if (rawIdEnvio.isNullOrBlank()) {
            log.warn("Webhook Efí payout: sem idEnvio. payload={}", payload)
            return ResponseEntity.ok().build()
        }

        // Compat: aceita "payout-<id>" (legado) e "P<id>" (novo). Tenta também parse direto.
        val orderId = when {
            rawIdEnvio.startsWith("payout-") -> rawIdEnvio.removePrefix("payout-").toLongOrNull()
            rawIdEnvio.startsWith("P")       -> rawIdEnvio.removePrefix("P").toLongOrNull()
            else                             -> rawIdEnvio.toLongOrNull()
        }

        if (orderId == null) {
            log.warn("Webhook Efí payout: idEnvio sem padrão esperado: {}", rawIdEnvio)
            return ResponseEntity.ok().build()
        }

        // lê valores que precisamos para o e-mail (valor líquido, chave pix e status atual)
        val row = jdbc.queryForMap(
            """
            SELECT amount_net, pix_key, status
              FROM payment_payouts
             WHERE order_id=:id
            """.trimIndent(),
            mapOf("id" to orderId)
        )
        val amountNet = (row["amount_net"] as? java.math.BigDecimal) ?: BigDecimal.ZERO
        val pixKey = (row["pix_key"] as? String).orEmpty()
        val currentStatus = (row["status"] as? String).orEmpty()

        when (status) {
            "REALIZADO" -> {
                // Verifica se já está CONFIRMED para evitar email duplicado
                val alreadyConfirmed = currentStatus == "CONFIRMED"
                
                // marca CONFIRMED (idempotente: se já estiver CONFIRMED, não muda nada)
                jdbc.update(
                    """UPDATE payment_payouts
                          SET status='CONFIRMED', confirmed_at=COALESCE(confirmed_at, NOW()), provider_ref=:ref
                        WHERE order_id=:id""",
                    mapOf("id" to orderId, "ref" to rawIdEnvio)
                )
                
                if (alreadyConfirmed) {
                    log.info("Webhook Efí payout: order #{} já estava CONFIRMED (idEnvio={}, valor={}) - ignorando email duplicado", orderId, rawIdEnvio, amountNet)
                } else {
                    log.info("Webhook Efí payout: CONFIRMED order #{} idEnvio={} valor={}", orderId, rawIdEnvio, amountNet)

                    // dispara e-mail de sucesso (apenas se não estava CONFIRMED antes)
                    runCatching {
                        mail.sendPayoutConfirmedEmail(
                            orderId = orderId,
                            amount = amountNet,
                            payeePixKey = pixKey,
                            idEnvio = rawIdEnvio,
                            endToEndId = endToEndId,
                            txid = txid,
                            extraNote = "Confirmação recebida da Efí via webhook."
                        )
                    }.onFailure { e ->
                        log.error("MAIL payout CONFIRMED falhou order #{}: {}", orderId, e.message, e)
                    }
                }
            }
            "NAO_REALIZADO" -> {
                // marca FAILED
                jdbc.update(
                    """UPDATE payment_payouts
                          SET status='FAILED', failed_at=NOW(), fail_reason='NAO_REALIZADO', provider_ref=:ref
                        WHERE order_id=:id""",
                    mapOf("id" to orderId, "ref" to rawIdEnvio)
                )
                log.warn("Webhook Efí payout: FAILED order #{} idEnvio={} valor={}", orderId, rawIdEnvio, amountNet)

                // dispara e-mail de falha
                runCatching {
                    mail.sendPayoutFailedEmail(
                        orderId = orderId,
                        amount = amountNet,
                        payeePixKey = pixKey,
                        idEnvio = rawIdEnvio,
                        errorCode = "NAO_REALIZADO",
                        errorMsg = "Efí sinalizou não realizado.",
                        txid = txid,
                        endToEndId = endToEndId,
                        extraNote = "Falha reportada pela Efí via webhook."
                    )
                }.onFailure { e ->
                    log.error("MAIL payout FAILED falhou order #{}: {}", orderId, e.message, e)
                }
            }
            else -> {
                log.info("Webhook Efí payout: idEnvio={}, status={} (ignorando)", rawIdEnvio, status)
            }
        }
        return ResponseEntity.ok().build()
    }
}
