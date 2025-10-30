package com.luizgasparetto.backend.monolito.web

import com.luizgasparetto.backend.monolito.config.efi.EfiAuthException
import com.luizgasparetto.backend.monolito.config.efi.EfiClientException
import com.luizgasparetto.backend.monolito.config.efi.EfiRateLimitException
import com.luizgasparetto.backend.monolito.config.efi.EfiServerException
import org.slf4j.MDC
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.OffsetDateTime

data class ApiError(
    val timestamp: String = OffsetDateTime.now().toString(),
    val status: Int,
    val error: String,
    val message: String?,
    val transferId: String? = MDC.get("transferId"),
    val details: Any? = null
)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(EfiRateLimitException::class)
    fun handle429(ex: EfiRateLimitException): ResponseEntity<ApiError> =
        ResponseEntity.status(429).body(
            ApiError(
                status = 429,
                error = "Too Many Requests",
                message = ex.message,
                details = mapOf("retryAfterSeconds" to ex.retryAfterSeconds, "body" to ex.responseBody)
            )
        )

    @ExceptionHandler(EfiAuthException::class)
    fun handleAuth(ex: EfiAuthException): ResponseEntity<ApiError> {
        val code = ex.status?.value() ?: 401
        return ResponseEntity.status(code).body(
            ApiError(
                status = code,
                error = "Auth Error",
                message = ex.message,
                details = ex.responseBody
            )
        )
    }

    @ExceptionHandler(EfiClientException::class)
    fun handle4xx(ex: EfiClientException): ResponseEntity<ApiError> {
        val code = ex.status?.value() ?: 400
        return ResponseEntity.status(code).body(
            ApiError(
                status = code,
                error = "Client Error",
                message = ex.message,
                details = ex.responseBody
            )
        )
    }

    @ExceptionHandler(EfiServerException::class)
    fun handle5xx(ex: EfiServerException): ResponseEntity<ApiError> {
        val code = ex.status?.value() ?: 502
        return ResponseEntity.status(code).body(
            ApiError(
                status = code,
                error = "Upstream Error",
                message = ex.message,
                details = ex.responseBody
            )
        )
    }
}
