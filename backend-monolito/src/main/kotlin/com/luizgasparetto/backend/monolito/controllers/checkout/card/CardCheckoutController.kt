package com.luizgasparetto.backend.monolito.controllers.checkout.card

import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.card.CardCheckoutResponse
import com.luizgasparetto.backend.monolito.services.card.CardCheckoutService
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("${ApiRoutes.API_V1}/checkout/card")
class CardCheckoutController(
    private val cardCheckoutService: CardCheckoutService
) {
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun checkoutCard(@Valid @RequestBody request: CardCheckoutRequest): ResponseEntity<CardCheckoutResponse> =
        ResponseEntity.ok(cardCheckoutService.processCardCheckout(request))
}

//