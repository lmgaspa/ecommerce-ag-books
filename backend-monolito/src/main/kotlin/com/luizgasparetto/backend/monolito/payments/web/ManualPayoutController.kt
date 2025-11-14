// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/ManualPayoutController.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.luizgasparetto.backend.monolito.web.ApiRoutes
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("${ApiRoutes.API_V1}/internal/payouts")
class ManualPayoutController(
    private val trigger: PaymentTriggerService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{orderId}/trigger")
    fun triggerManual(
        @PathVariable orderId: Long,
        @RequestParam(required = false) pixKey: String?
    ): ResponseEntity<PayoutResult> {
        val txid = "MANUAL-" + UUID.randomUUID().toString().take(8)
        log.info("Manual payout trigger: orderId={}, txid={}, overridePixKey={}", orderId, txid, pixKey?.let { "****${it.takeLast(3)}" })

        val result = trigger.tryTriggerByRef(
            orderRef = orderId.toString(),
            externalId = txid,
            sourceProvider = "MANUAL",
            overridePixKey = pixKey?.takeIf { it.isNotBlank() }  // <- usa override só nesta chamada
        )
        return ResponseEntity.ok(result)
    }
}
