package com.luizgasparetto.backend.monolito.config.payments

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("efi.card.payout")
data class EfiCardPayoutProps(
    val realFeePercent1x: Double = 3.49,
    val realFeePercent2to6x: Double = 3.79,
    val realFeePercent7to12x: Double = 4.39,
    val favoredKey: String? = null,
    val feePercent: Double = 4.39,
    val feeFixed: Double = 0.0,
    val marginPercent: Double = 0.5,
    val marginFixed: Double = 0.0,
    val minSend: Double = 1.20,
    val fees: Fees = Fees(),
    val delay: Delay = Delay(),
    val scheduler: Scheduler = Scheduler()
) {
    data class Fees(val includeGatewayFees: Boolean = true)
    data class Delay(val cardDays: Int = 32, val pixMinutes: Int = 0)
    data class Scheduler(
        val card: CardScheduler = CardScheduler()
    ) {
        data class CardScheduler(
            val cron: String = "0 17 3 * * *",
            val batchSize: Int = 100
        )
    }
}

