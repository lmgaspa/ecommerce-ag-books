package com.luizgasparetto.backend.monolito.dto.card

data class CardCheckoutResponse(
    val success: Boolean,
    val message: String,
    val orderId: String,
    val chargeId: String?,   // id da cobrança retornado pela Efí (pode ser nulo em falha)
    val status: String       // ex.: "approved", "processing", "failed"
)