package com.luizgasparetto.backend.monolito.controllers.checkout

import com.luizgasparetto.backend.monolito.dto.pix.PixCheckoutRequest
import com.luizgasparetto.backend.monolito.dto.pix.PixCheckoutResponse
import com.luizgasparetto.backend.monolito.services.pix.PixCheckoutService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/checkout")
class PixCheckoutController(
    private val checkoutService: PixCheckoutService
) {
    @PostMapping
    fun checkoutPix(@RequestBody request: PixCheckoutRequest): ResponseEntity<PixCheckoutResponse> =
        ResponseEntity.ok(checkoutService.processCheckout(request))
}
