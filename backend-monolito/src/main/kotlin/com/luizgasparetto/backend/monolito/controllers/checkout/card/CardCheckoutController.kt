package com.luizgasparetto.backend.monolito.controllers.checkout

import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutResponse
import com.luizgasparetto.backend.monolito.services.card.CardCheckoutService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/checkout/card")
class CardCheckoutController(
    private val cardCheckoutService: CardCheckoutService
) {
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun checkoutCard(@RequestBody request: CardCheckoutRequest): ResponseEntity<CardCheckoutResponse> =
        ResponseEntity.ok(cardCheckoutService.processCardCheckout(request))
}
