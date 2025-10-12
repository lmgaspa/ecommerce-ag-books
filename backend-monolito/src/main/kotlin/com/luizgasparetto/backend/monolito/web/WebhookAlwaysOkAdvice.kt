package com.luizgasparetto.backend.monolito.payment.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = [PaymentWebhookController::class])
class WebhookAlwaysOkAdvice {
    @ExceptionHandler(Exception::class)
    fun always200(@Suppress("UnusedParameter") req: HttpServletRequest, ex: Exception): ResponseEntity<Void> =
        ResponseEntity.ok().build()
}
