// src/main/kotlin/com/luizgasparetto/backend/monolito/models/autopayout/response/SentTransferItem.kt
package com.luizgasparetto.backend.monolito.models.autopayout.response

data class SentTransferItem(
    val endToEndId: String?,
    val transferId: String?,
    val amount: String?,
    val status: String?,
    val requestedAt: String?,
    val settledAt: String?
)
