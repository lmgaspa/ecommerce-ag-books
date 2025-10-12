package com.luizgasparetto.backend.monolito.payment.web

import com.luizgasparetto.backend.monolito.payment.services.PaymentTriggerService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import com.luizgasparetto.backend.monolito.payment.services.PaymentRawEventService
@RestController
@RequestMapping("/api/webhooks/payment")
class PaymentWebhookController(
    private val rawEvents: PaymentRawEventService,
    private val trigger: PaymentTriggerService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/pix")
    fun onPix(@RequestBody payload: Map<String, Any?>): ResponseEntity<Void> {
        val txid   = payload["txid"]?.toString().orEmpty()
        val status = payload["status"]?.toString()?.uppercase().orEmpty()
        val orderRef = (payload["metadata"] as? Map<*, *>)?.get("orderId")?.toString()

        // grava sempre o evento bruto
        runCatching {
            rawEvents.saveRaw(
                provider = "PIX",
                eventType = if (status in setOf("PAID","CONCLUIDA")) "pix.paid" else "pix.other",
                externalId = txid,
                orderRef = orderRef,
                payload = payload
            )
        }.onFailure { e ->
            log.error("Falha registrando raw PIX (txid={}, orderRef={}): {}", txid, orderRef, e.message)
        }

        // dispara orquestração só se pago
        if (status in setOf("PAID","CONCLUIDA")) {
            runCatching {
                trigger.tryTriggerByRef(orderRef = orderRef, externalId = txid, provider = "PIX")
            }.onFailure { e ->
                log.error("Falha processando repasse PIX (txid={}, orderRef={}): {}", txid, orderRef, e.message)
                // não relança: webhook deve responder 200
            }
        }

        return ResponseEntity.ok().build()
    }
}
