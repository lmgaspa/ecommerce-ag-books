package com.luizgasparetto.backend.monolito.payments.web

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks/payment")
class PaymentWebhookController(
    private val rawEvents: PaymentRawEventService,
    private val trigger: PaymentTriggerService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/pix")
    fun onPix(@RequestBody payload: Map<String, Any?>): ResponseEntity<Void> {
        val txid     = payload["txid"]?.toString().orEmpty()
        val status   = payload["status"]?.toString()?.uppercase().orEmpty()
        val orderRef = (payload["metadata"] as? Map<*, *>)?.get("orderId")?.toString()

        try {
            rawEvents.saveRaw(
                provider   = "PIX",
                eventType  = if (status in setOf("PAID","CONCLUIDA")) "pix.paid" else "pix.other",
                externalId = txid,
                orderRef   = orderRef,
                payload    = payload
            )
        } catch (e: Exception) {
            log.error("Falha registrando raw PIX (txid={}, orderRef={}): {}", txid, orderRef, e.message)
        }

        if (status in setOf("PAID","CONCLUIDA")) {
            try {
                trigger.tryTriggerByRef(orderRef = orderRef, externalId = txid, provider = "PIX")
            } catch (e: Exception) {
                log.error("Falha processando repasse PIX (txid={}, orderRef={}): {}", txid, orderRef, e.message)
            }
        }
        return ResponseEntity.ok().build()
    }
}
