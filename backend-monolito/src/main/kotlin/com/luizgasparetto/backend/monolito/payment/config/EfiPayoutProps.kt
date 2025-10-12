// src/main/kotlin/com/luizgasparetto/backend/monolito/config/EfiPayoutProps.kt
package com.luizgasparetto.backend.monolito.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties("efi.payout")
data class EfiPayoutProps(
    val favoredKey: String? = null,
    val feePercent: BigDecimal = BigDecimal.ZERO,   // ex.: 1.59
    val feeFixed: BigDecimal = BigDecimal.ZERO,     // ex.: 0.49
    val marginPercent: BigDecimal = BigDecimal("0.8"),
    val marginFixed: BigDecimal = BigDecimal.ZERO,
    val minSend: BigDecimal = BigDecimal("1.00")
)
