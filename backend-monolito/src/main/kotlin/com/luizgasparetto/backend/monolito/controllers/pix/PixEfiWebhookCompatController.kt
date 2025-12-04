// src/main/kotlin/com/luizgasparetto/backend/monolito/controllers/pix/PixEfiWebhookCompatController.kt
package com.luizgasparetto.backend.monolito.controllers.pix

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.models.webhook.WebhookEvent
import com.luizgasparetto.backend.monolito.payments.web.PaymentTriggerService
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.WebhookEventRepository
import com.luizgasparetto.backend.monolito.services.email.pix.PixEmailService
import com.luizgasparetto.backend.monolito.services.order.OrderEventsPublisher
import com.luizgasparetto.backend.monolito.payments.web.EfiPixSendWebhookController
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

/**
 * Controller de compatibilidade para o endpoint incorreto que a EFI ainda envia:
 * /api/webhooks/payment/pix/pix (sem /v1 e com /pix duplicado)
 * 
 * Este controller processa os webhooks da mesma forma que PixEfiWebhookController,
 * mas aceita o endpoint incorreto para evitar erros 500.
 */
@RestController
@RequestMapping("/api/webhooks/payment/pix")
class PixEfiWebhookCompatController(
    private val orderRepository: OrderRepository,
    private val emailService: PixEmailService,
    private val mapper: ObjectMapper,
    private val events: OrderEventsPublisher,
    private val webhookRepo: WebhookEventRepository,
    private val payoutTrigger: PaymentTriggerService,
    private val payoutWebhook: EfiPixSendWebhookController
) {
    private val log = LoggerFactory.getLogger(PixEfiWebhookCompatController::class.java)

    @PostMapping("/pix", consumes = ["application/json"])
    @Transactional
    fun handle(@RequestBody rawBody: String): ResponseEntity<String> {
        log.warn("EFI WEBHOOK COMPAT: recebido no endpoint INCORRETO /api/webhooks/payment/pix/pix - processando normalmente")
        log.info("EFI WEBHOOK RAW={}", rawBody.take(5000))

        val root = runCatching { mapper.readTree(rawBody) }.getOrElse {
            log.warn("EFI WEBHOOK: JSON inválido: {}", it.message)
            webhookRepo.save(
                WebhookEvent(
                    txid = null,
                    status = "INVALID_JSON",
                    chargeId = null,
                    provider = "PIX",
                    rawBody = rawBody,
                    receivedAt = OffsetDateTime.now()
                )
            )
            return ResponseEntity.ok("⚠️ Ignorado: JSON inválido")
        }

        val pix0 = root.path("pix").takeIf { it.isArray && it.size() > 0 }?.get(0)
        
        // Detecta se é webhook de repasse (tem idEnvio mas não tem txid)
        val idEnvio = when {
            !root.path("idEnvio").isMissingNode -> root.path("idEnvio").asText()
            pix0 != null && !pix0.path("idEnvio").isMissingNode -> pix0.path("idEnvio").asText()
            pix0 != null && !pix0.path("gnExtras").isMissingNode && !pix0.path("gnExtras").path("idEnvio").isMissingNode -> 
                pix0.path("gnExtras").path("idEnvio").asText()
            else -> null
        }?.takeIf { it.isNotBlank() }
        
        val txid = when {
            !root.path("txid").isMissingNode -> root.path("txid").asText()
            pix0 != null && !pix0.path("txid").isMissingNode -> pix0.path("txid").asText()
            else -> null
        }?.takeIf { it.isNotBlank() }

        val status = when {
            !root.path("status").isMissingNode -> root.path("status").asText()
            pix0 != null && !pix0.path("status").isMissingNode -> pix0.path("status").asText()
            else -> null
        }

        // Se tem idEnvio mas não tem txid, é webhook de repasse - redireciona
        if (idEnvio != null && txid == null) {
            log.info("EFI WEBHOOK COMPAT: detectado webhook de REPASSE (idEnvio={}, status={}) - redirecionando", idEnvio, status)
            try {
                val payoutStatus = status?.uppercase() ?: pix0?.path("status")?.asText()?.uppercase()
                val endToEndId = when {
                    !root.path("endToEndId").isMissingNode -> root.path("endToEndId").asText()
                    pix0 != null && !pix0.path("endToEndId").isMissingNode -> pix0.path("endToEndId").asText()
                    else -> null
                }

                val payload = mutableMapOf<String, Any?>(
                    "idEnvio" to idEnvio,
                    "status" to payoutStatus
                )
                if (endToEndId != null) payload["endToEndId"] = endToEndId

                payoutWebhook.onSendWebhook(payload)
                return ResponseEntity.ok("✅ Webhook de repasse processado")
            } catch (e: Exception) {
                log.error("EFI WEBHOOK COMPAT: falha ao processar webhook de repasse: {}", e.message, e)
                return ResponseEntity.ok("⚠️ Erro ao processar webhook de repasse")
            }
        }

        // Log/auditoria
        webhookRepo.save(
            WebhookEvent(
                txid = txid,
                status = status,
                chargeId = null,
                provider = "PIX",
                rawBody = rawBody,
                receivedAt = OffsetDateTime.now()
            )
        )

        log.info("EFI WEBHOOK COMPAT PARSED txid={}, status={}", txid, status)
        if (txid == null) return ResponseEntity.ok("⚠️ Ignorado: txid ausente")

        val order = orderRepository.findWithItemsByTxid(txid)
            ?: return ResponseEntity.ok("⚠️ Ignorado: pedido não encontrado para txid=$txid")

        val paidStatuses = setOf(
            "CONCLUIDA","CONCLUÍDA","LIQUIDADO","LIQUIDADA",
            "ATIVA-RECEBIDA","COMPLETED","PAID","CONFIRMADA","CONFIRMADO"
        )
        val shouldMarkPaid = status.isNullOrBlank() || paidStatuses.contains(status.uppercase())
        if (!shouldMarkPaid) return ResponseEntity.ok("ℹ️ Ignorado: status=$status não indica pagamento")
        if (order.paid) return ResponseEntity.ok("ℹ️ Ignorado: pedido já estava pago")

        val now = OffsetDateTime.now()
        val reservaValida = order.status == OrderStatus.WAITING &&
                (order.reserveExpiresAt == null || now.isBefore(order.reserveExpiresAt))

        return if (reservaValida) {
            order.paid = true
            order.paidAt = now
            order.status = OrderStatus.CONFIRMED
            orderRepository.save(order)

            runCatching {
                emailService.sendPixClientEmail(order)
                emailService.sendPixAuthorEmail(order)
            }.onFailure { e ->
                log.error("EFI WEBHOOK COMPAT: falha ao enviar e-mails do order {}: {}", order.id, e.message, e)
            }

            runCatching {
                payoutTrigger.tryTriggerByRef(
                    orderRef = order.id?.toString(),
                    externalId = txid,
                    sourceProvider = "PIX-WEBHOOK-COMPAT"
                )
            }.onFailure { e ->
                log.error("EFI WEBHOOK COMPAT: payout trigger falhou (orderId={}, txid={}): {}", order.id, txid, e.message)
            }

            order.id?.let { runCatching { events.publishPaid(it) } }
            ResponseEntity.ok("✅ Pago; reserva válida; pedido CONFIRMED; e-mails enviados; repasse disparado")
        } else {
            order.status = OrderStatus.REFUNDED
            orderRepository.save(order)
            log.warn("Pagamento recebido após expiração da reserva. txid={}, orderId={}", txid, order.id)
            ResponseEntity.ok("⚠️ Pago após expiração; pedido cancelado/estorno")
        }
    }
}

