package com.luizgasparetto.backend.monolito.dto.pix

data class PixCheckoutResponse(
    val qrCode: String,
    val qrCodeBase64: String,
    val message: String,
    val orderId: String,
    val txid: String,
    val reserveExpiresAt: String? = null, // ISO-8601 OffsetDateTime
    val ttlSeconds: Long? = null,
    val warningAt: Int? = null, // Avisar quando faltar X segundos
    val securityWarningAt: Int? = null // Aviso de segurança quando faltar X segundos
)
