// src/main/kotlin/com/luizgasparetto/backend/monolito/models/autopayout/request/AutoPayoutRequest.kt
package com.luizgasparetto.backend.monolito.models.autopayout.request

data class AutoPayoutRequest(
    val amount: String,           // "12.34"
    val favoredKey: String?,      // opcional (se não vier, usa cfg.payoutFavoredKey)
    val info: String?             // opcional
)
