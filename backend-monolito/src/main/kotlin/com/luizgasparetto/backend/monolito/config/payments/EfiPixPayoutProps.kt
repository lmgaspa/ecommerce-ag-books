package com.luizgasparetto.backend.monolito.config.payments

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("efi.pix.payout")
data class EfiPixPayoutProps(
    val realFeePercent: Double = 1.19,
    val feePercent: Double = 1.19,
    val feeFixed: Double = 0.0,
    val marginPercent: Double = 0.5,
    val marginFixed: Double = 0.0,
    val minSend: Double = 1.20,
    val fees: Fees = Fees()
) {
    data class Fees(val includeGatewayFees: Boolean = true)
}

