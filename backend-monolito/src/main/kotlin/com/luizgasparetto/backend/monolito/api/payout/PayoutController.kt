// src/main/kotlin/.../api/payout/PayoutController.kt
package com.luizgasparetto.backend.monolito.api.payout

import com.luizgasparetto.backend.monolito.services.payout.PayoutCalculator
import com.luizgasparetto.backend.monolito.services.payout.PayoutSendService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payouts")
class PayoutController(
    private val calculator: PayoutCalculator,
    private val sender: PayoutSendService
) {
    @PostMapping("/preview")
    fun preview(@RequestBody req: PayoutPreviewReq) = calculator.preview(req)

    @PostMapping("/send")
    fun send(@RequestBody req: PayoutSendReq) = sender.send(req)
}
