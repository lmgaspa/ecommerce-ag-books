// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/PaymentWebhookController.kt
package com.luizgasparetto.backend.monolito.payments.web

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.luizgasparetto.backend.monolito.web.ApiRoutes

@RestController
@RequestMapping("${ApiRoutes.API_V1}/webhooks/payment")
class PaymentWebhookController(
    private val rawEvents: PaymentRawEventService,
    private val trigger: PaymentTriggerService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val paidStatuses = setOf("PAID", "PAGO", "CONCLUIDA", "CONCLUÍDA", "CONFIRMADA", "CONFIRMADO")

    @PostMapping(
        "/pix",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun onPix(@RequestBody payload: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val txid     = payload["txid"]?.toString()
        val status   = payload["status"]?.toString()?.uppercase()
        val orderRef = (payload["metadata"] as? Map<*, *>)?.get("orderId")?.toString()

        // sempre registra o raw (idempotente pelo UNIQUE do banco)
        runCatching {
            rawEvents.saveRaw(
                provider   = "EFI_PIX",
                eventType  = if (status != null && status in paidStatuses) "pix.paid" else "pix.other",
                externalId = txid.orEmpty(),            // <- ✅ evita String? aqui
                orderRef   = orderRef,
                payload    = payload
            )
        }.onFailure { e ->
            log.error("RAW-PIX save fail txid={} orderRef={} err={}", txid.orEmpty(), orderRef, e.message)
        }

        // dispara repasse só se pago
        if (status != null && status in paidStatuses) {
            runCatching {
                trigger.tryTriggerByRef(
                    orderRef = orderRef,
                    externalId = txid.orEmpty(),        // <- ✅ idem aqui
                    sourceProvider = "PIX-WEBHOOK"
                )
            }.onFailure { e ->
                log.error("PAYOUT trigger fail txid={} orderRef={} err={}", txid.orEmpty(), orderRef, e.message)
            }
        }

        return ResponseEntity.ok(mapOf("ok" to true))
    }

    // Webhook de repasse PIX (payout)
    @PostMapping("/payout/pix", consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun onPixPayout(@RequestBody payload: Map<String, Any?>): ResponseEntity<Map<String, Any?>> =
        onPix(payload)
}
