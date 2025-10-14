package com.luizgasparetto.backend.monolito.payments.web

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks/payment")
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
        val txid     = payload["txid"]?.toString().orEmpty()
        val status   = payload["status"]?.toString()?.uppercase().orEmpty()
        val orderRef = (payload["metadata"] as? Map<*, *>)?.get("orderId")?.toString()

        // sempre registra o raw (idempotente pelo UNIQUE do banco)
        runCatching {
            rawEvents.saveRaw(
                provider   = "EFI_PIX",
                eventType  = if (status in paidStatuses) "pix.paid" else "pix.other",
                externalId = txid,
                orderRef   = orderRef,
                payload    = payload
            )
        }.onFailure { e ->
            log.error("RAW-PIX save fail txid={} orderRef={} err={}", txid, orderRef, e.message)
        }

        // dispara repasse só se pago
        if (status in paidStatuses) {
            runCatching {
                trigger.tryTriggerByRef(
                    orderRef = orderRef,
                    externalId = txid,
                    sourceProvider = "PIX-WEBHOOK"
                )
            }.onFailure { e ->
                log.error("PAYOUT trigger fail txid={} orderRef={} err={}", txid, orderRef, e.message)
            }
        }

        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
