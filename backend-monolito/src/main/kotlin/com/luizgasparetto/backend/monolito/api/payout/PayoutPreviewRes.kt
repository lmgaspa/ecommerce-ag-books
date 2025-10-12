// src/main/kotlin/com/luizgasparetto/backend/monolito/api/payout/PayoutPreviewRes.kt
package com.luizgasparetto.backend.monolito.api.payout

import java.math.BigDecimal

data class PayoutPreviewRes(
    val gross: BigDecimal,
    val refunds: BigDecimal,
    val shippingExcluded: BigDecimal,
    val base: BigDecimal,

    val efipayFeePercent: BigDecimal,
    val efipayFeeFixed: BigDecimal,
    val fee: BigDecimal,

    val marginPercent: BigDecimal,
    val marginFixed: BigDecimal,
    val margin: BigDecimal,

    val net: BigDecimal,
    val canSend: Boolean,
    val minSend: BigDecimal,

    val note: String? = null
)
