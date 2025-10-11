package com.luizgasparetto.backend.monolito.services.autopayout

import com.luizgasparetto.backend.monolito.config.payout.BusinessPayoutConfig
import java.math.BigDecimal
import java.math.RoundingMode

data class PayoutBreakdown(
    val gross: BigDecimal,
    val efiFee: BigDecimal,
    val merchantMargin: BigDecimal,
    val netToSend: BigDecimal
)

object PayoutCalculator {
    fun computeNetToSend(
        gross: BigDecimal,
        cfg: BusinessPayoutConfig
    ): PayoutBreakdown {
        val scale = 2
        val hundred = BigDecimal("100")

        val feePct = cfg.feePercent.divide(hundred)
        val marginPct = cfg.marginPercent.divide(hundred)

        val efiFee = gross.multiply(feePct).setScale(scale, RoundingMode.HALF_UP)
            .add(cfg.feeFixed).setScale(scale, RoundingMode.HALF_UP)

        val merchantMargin = gross.multiply(marginPct).setScale(scale, RoundingMode.HALF_UP)
            .add(cfg.marginFixed).setScale(scale, RoundingMode.HALF_UP)

        val net = gross.subtract(efiFee).subtract(merchantMargin).setScale(scale, RoundingMode.HALF_UP)

        return PayoutBreakdown(
            gross = gross.setScale(scale, RoundingMode.HALF_UP),
            efiFee = efiFee,
            merchantMargin = merchantMargin,
            netToSend = net
        )
    }
}
