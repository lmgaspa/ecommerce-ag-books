// src/main/kotlin/com/luizgasparetto/backend/monolito/controllers/autopayout/AutoPayoutController.kt
package com.luizgasparetto.backend.monolito.controllers.autopayout

import com.luizgasparetto.backend.monolito.models.autopayout.request.AutoPayoutRequest
import com.luizgasparetto.backend.monolito.models.autopayout.response.AutoPayoutResponse
import com.luizgasparetto.backend.monolito.services.AutoPayoutService
import com.luizgasparetto.backend.monolito.services.autopayout.SentTransfersQueryService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/autopayout")
class AutoPayoutController(
    private val service: AutoPayoutService,
    private val queryService: SentTransfersQueryService
) {
    @PutMapping("/{transferId}")
    fun send(
        @PathVariable transferId: String,
        @RequestBody req: AutoPayoutRequest
    ): AutoPayoutResponse = service.send(transferId, req)

    @GetMapping("/sent")
    fun listSent(
        @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: OffsetDateTime,
        @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: OffsetDateTime,
        @RequestParam(required = false) status: String?
    ) = queryService.listSent(from, to, status)
}
