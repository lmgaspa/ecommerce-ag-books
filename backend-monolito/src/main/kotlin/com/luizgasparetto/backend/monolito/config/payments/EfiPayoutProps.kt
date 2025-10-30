// src/main/kotlin/com/luizgasparetto/backend/monolito/config/EfiPayoutProps.kt
package com.luizgasparetto.backend.monolito.config.payments

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("efi.payout")
data class EfiPayoutProps(
    val favoredKey: String? = null,
    val feePercent: Double = 0.0,
    val feeFixed: Double = 0.0,
    val marginPercent: Double = 0.0,
    val marginFixed: Double = 0.0,
    val minSend: Double = 0.0,
    val delay: Delay = Delay(),
    val fees: Fees = Fees(),
    val behavior: Behavior = Behavior()
) {
    data class Delay(val cardDays: Int = 30, val pixMinutes: Int = 0)
    data class Fees(val includeGatewayFees: Boolean = true)
    data class Behavior(val onePayoutPerAuthorPerOrder: Boolean = true)
}
