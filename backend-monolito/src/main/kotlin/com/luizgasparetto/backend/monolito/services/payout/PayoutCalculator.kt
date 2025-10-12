// src/main/kotlin/com/luizgasparetto/backend/monolito/services/payout/PayoutCalculator.kt
package com.luizgasparetto.backend.monolito.services.payout

import com.luizgasparetto.backend.monolito.api.payout.PayoutPreviewReq
import com.luizgasparetto.backend.monolito.api.payout.PayoutPreviewRes
import com.luizgasparetto.backend.monolito.config.EfiPayoutProps
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PayoutCalculator(
    private val props: EfiPayoutProps
) {
    private fun nz(v: BigDecimal?) = v ?: BigDecimal.ZERO
    private fun BigDecimal.round2() = this.setScale(2, RoundingMode.HALF_UP)

    fun preview(req: PayoutPreviewReq): PayoutPreviewRes {
        val gross = nz(req.gross).round2()
        val shipping = nz(req.shipping).round2()

        // refunds: soma da lista (se vier), senão usa refundsTotal
        val refunds = (req.refunds?.fold(BigDecimal.ZERO) { acc, r -> acc + nz(r) }
            ?: nz(req.refundsTotal)).round2()

        // base
        var base = (gross - refunds)
        val shippingExcluded = if (req.includeShippingInBase) BigDecimal.ZERO else shipping
        base = (base - shippingExcluded).let { if (it.signum() < 0) BigDecimal.ZERO else it }

        // parâmetros (override > props)
        val feePct       = req.overrideFeePercent    ?: props.feePercent
        val feeFixed     = req.overrideFeeFixed      ?: props.feeFixed
        val marginPct    = req.overrideMarginPercent ?: props.marginPercent
        val marginFixed  = req.overrideMarginFixed   ?: props.marginFixed
        val minSend      = props.minSend

        // taxa Efí e margem
        val fee = (base * feePct.movePointLeft(2) + feeFixed).let { if (it.signum() < 0) BigDecimal.ZERO else it }.round2()
        val margin = (base * marginPct.movePointLeft(2) + marginFixed).let { if (it.signum() < 0) BigDecimal.ZERO else it }.round2()

        // líquido
        val net = (base - fee - margin).let { if (it.signum() < 0) BigDecimal.ZERO else it }.round2()
        val canSend = net >= minSend

        val note = if (!canSend) "Valor líquido abaixo do mínimo de envio (minSend)." else "OK para repassar."

        return PayoutPreviewRes(
            gross = gross,
            refunds = refunds,
            shippingExcluded = shippingExcluded.round2(),
            base = base.round2(),

            efipayFeePercent = feePct.setScale(2, RoundingMode.HALF_UP),
            efipayFeeFixed = feeFixed.setScale(2, RoundingMode.HALF_UP),
            fee = fee,

            marginPercent = marginPct.setScale(2, RoundingMode.HALF_UP),
            marginFixed = marginFixed.setScale(2, RoundingMode.HALF_UP),
            margin = margin,

            net = net,
            canSend = canSend,
            minSend = minSend.setScale(2, RoundingMode.HALF_UP),

            note = note
        )
    }
}
