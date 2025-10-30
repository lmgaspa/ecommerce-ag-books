package com.luizgasparetto.backend.monolito.dto.card

data class CardCheckoutResponse(
    val success: Boolean,
    val message: String,
    val orderId: String,
    val chargeId: String? = null,
    val status: String? = null,
    val reserveExpiresAt: String? = null, // ISO-8601 OffsetDateTime
    val ttlSeconds: Long? = null,
    val warningAt: Int? = null, // Avisar quando faltar X segundos
    val securityWarningAt: Int? = null // Aviso de segurança quando faltar X segundos
)