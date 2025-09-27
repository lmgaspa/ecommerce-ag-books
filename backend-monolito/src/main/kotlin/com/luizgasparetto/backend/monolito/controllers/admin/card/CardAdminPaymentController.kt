package com.luizgasparetto.backend.monolito.controllers.admin.card

import com.luizgasparetto.backend.monolito.services.card.CardPaymentProcessor
import com.luizgasparetto.backend.monolito.services.card.CardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/payments/card")
class CardAdminPaymentController(
    private val cardService: CardService,
    private val processor: CardPaymentProcessor
) {
    @PostMapping("/reprocess/{chargeId}")
    fun reprocess(@PathVariable chargeId: String): ResponseEntity<String> {
        val status = cardService.getChargeStatus(chargeId)
        val applied = processor.isCardPaidStatus(status) && processor.markPaidIfNeededByChargeId(chargeId)
        return ResponseEntity.ok("status=$status; applied=$applied")
    }
}
