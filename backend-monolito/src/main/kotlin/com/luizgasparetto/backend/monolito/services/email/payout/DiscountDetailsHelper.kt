package com.luizgasparetto.backend.monolito.services.email.payout

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import com.luizgasparetto.backend.monolito.config.payments.EfiPixPayoutProps
import com.luizgasparetto.backend.monolito.config.payments.EfiCardPayoutProps
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Interface comum para propriedades de payout
 */
private interface PayoutPropsCommon {
    val feePercent: Double
    val feeFixed: Double
    val marginPercent: Double
    val marginFixed: Double
    val fees: Any
}

private val EfiPixPayoutProps.feesIncludeGateway: Boolean
    get() = fees.includeGatewayFees

private val EfiCardPayoutProps.feesIncludeGateway: Boolean
    get() = fees.includeGatewayFees

/**
 * Helper global para calcular e gerar HTML de detalhamento de descontos (taxas e margens)
 * usado em emails de repasse e pagamento ao autor.
 */
object DiscountDetailsHelper {

    /**
     * Data class para detalhamento do desconto aplicado no repasse
     */
    data class DiscountDetails(
        val fee: BigDecimal,
        val margin: BigDecimal,
        val totalDiscount: BigDecimal,
        val amountGross: BigDecimal,
        val amountNet: BigDecimal
    )

    /**
     * Calcula o detalhamento completo do desconto (fee, margin, total)
     * usando as mesmas regras do PaymentTriggerService
     */
    fun calculateDiscountDetails(
        amountGross: BigDecimal,
        payoutProps: EfiPayoutProps
    ): DiscountDetails = calculateDiscountDetailsInternal(amountGross, payoutProps)

    /**
     * Calcula o detalhamento completo do desconto para PIX
     */
    fun calculateDiscountDetails(
        amountGross: BigDecimal,
        payoutProps: EfiPixPayoutProps
    ): DiscountDetails = calculateDiscountDetailsInternal(amountGross, payoutProps)

    /**
     * Calcula o detalhamento completo do desconto para CARD
     */
    fun calculateDiscountDetails(
        amountGross: BigDecimal,
        payoutProps: EfiCardPayoutProps
    ): DiscountDetails = calculateDiscountDetailsInternal(amountGross, payoutProps)

    /**
     * Implementação interna comum para calcular descontos
     */
    private fun calculateDiscountDetailsInternal(
        amountGross: BigDecimal,
        feePercent: Double,
        feeFixed: Double,
        marginPercent: Double,
        marginFixed: Double,
        includeGatewayFees: Boolean
    ): DiscountDetails {
        val hundred = BigDecimal("100")
        val feePercentBd = BigDecimal.valueOf(feePercent)
        val feeFixedBd = BigDecimal.valueOf(feeFixed)
        val marginPercentBd = BigDecimal.valueOf(marginPercent)
        val marginFixedBd = BigDecimal.valueOf(marginFixed)

        val fee = if (includeGatewayFees) {
            amountGross.multiply(feePercentBd).divide(hundred, 2, RoundingMode.HALF_UP).plus(feeFixedBd)
        } else {
            BigDecimal.ZERO
        }
        val margin = amountGross.multiply(marginPercentBd).divide(hundred, 2, RoundingMode.HALF_UP).plus(marginFixedBd)

        val totalDiscount = fee.plus(margin).setScale(2, RoundingMode.HALF_UP)
        var net = amountGross.minus(totalDiscount).setScale(2, RoundingMode.HALF_UP)
        
        if (net >= amountGross) {
            net = amountGross.minus(BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP)
        }
        
        return DiscountDetails(
            fee = fee.setScale(2, RoundingMode.HALF_UP),
            margin = margin.setScale(2, RoundingMode.HALF_UP),
            totalDiscount = totalDiscount,
            amountGross = amountGross.setScale(2, RoundingMode.HALF_UP),
            amountNet = net
        )
    }

    private fun calculateDiscountDetailsInternal(
        amountGross: BigDecimal,
        payoutProps: EfiPayoutProps
    ): DiscountDetails = calculateDiscountDetailsInternal(
        amountGross,
        payoutProps.feePercent,
        payoutProps.feeFixed,
        payoutProps.marginPercent,
        payoutProps.marginFixed,
        payoutProps.fees.includeGatewayFees
    )

    private fun calculateDiscountDetailsInternal(
        amountGross: BigDecimal,
        payoutProps: EfiPixPayoutProps
    ): DiscountDetails = calculateDiscountDetailsInternal(
        amountGross,
        payoutProps.feePercent,
        payoutProps.feeFixed,
        payoutProps.marginPercent,
        payoutProps.marginFixed,
        payoutProps.feesIncludeGateway
    )

    private fun calculateDiscountDetailsInternal(
        amountGross: BigDecimal,
        payoutProps: EfiCardPayoutProps
    ): DiscountDetails = calculateDiscountDetailsInternal(
        amountGross,
        payoutProps.feePercent,
        payoutProps.feeFixed,
        payoutProps.marginPercent,
        payoutProps.marginFixed,
        payoutProps.feesIncludeGateway
    )

    /**
     * Gera o bloco HTML com detalhamento do desconto aplicado no repasse
     */
    fun buildDiscountDetailsBlock(
        details: DiscountDetails,
        payoutProps: EfiPayoutProps,
        efiRealFeePercent: Double? = null,
        paymentTypeLabel: String? = null,
        installmentsInfo: String? = null
    ): String = buildDiscountDetailsBlockInternal(
        details,
        payoutProps.marginPercent,
        payoutProps.marginFixed,
        efiRealFeePercent,
        paymentTypeLabel,
        installmentsInfo
    )

    /**
     * Gera o bloco HTML com detalhamento do desconto aplicado no repasse (PIX)
     */
    fun buildDiscountDetailsBlock(
        details: DiscountDetails,
        payoutProps: EfiPixPayoutProps,
        efiRealFeePercent: Double? = null,
        paymentTypeLabel: String? = null,
        installmentsInfo: String? = null
    ): String = buildDiscountDetailsBlockInternal(
        details,
        payoutProps.marginPercent,
        payoutProps.marginFixed,
        efiRealFeePercent,
        paymentTypeLabel,
        installmentsInfo
    )

    /**
     * Gera o bloco HTML com detalhamento do desconto aplicado no repasse (CARD)
     */
    fun buildDiscountDetailsBlock(
        details: DiscountDetails,
        payoutProps: EfiCardPayoutProps,
        efiRealFeePercent: Double? = null,
        paymentTypeLabel: String? = null,
        installmentsInfo: String? = null
    ): String = buildDiscountDetailsBlockInternal(
        details,
        payoutProps.marginPercent,
        payoutProps.marginFixed,
        efiRealFeePercent,
        paymentTypeLabel,
        installmentsInfo
    )

    /**
     * Implementação interna comum para gerar HTML
     */
    private fun buildDiscountDetailsBlockInternal(
        details: DiscountDetails,
        marginPercent: Double,
        marginFixed: Double,
        efiRealFeePercent: Double?,
        paymentTypeLabel: String?,
        installmentsInfo: String?
    ): String {
        val grossFmt = "R$ %.2f".format(details.amountGross.toDouble())
        val feeFmt = "R$ %.2f".format(details.fee.toDouble())
        val marginFmt = "R$ %.2f".format(details.margin.toDouble())
        val discountFmt = "R$ %.2f".format(details.totalDiscount.toDouble())
        val netFmt = "R$ %.2f".format(details.amountNet.toDouble())
        
        val marginPercentFmt = "%.2f%%".format(marginPercent)
        
        // Calcula e formata a tarifa real da Efí Bank se fornecida
        val efiFeeLine = if (efiRealFeePercent != null && paymentTypeLabel != null) {
            val hundred = BigDecimal("100")
            val efiFeePercent = BigDecimal.valueOf(efiRealFeePercent)
            val efiFeeReal = details.amountGross
                .multiply(efiFeePercent)
                .divide(hundred, 2, RoundingMode.HALF_UP)
            
            val efiFeePercentFmt = "%.2f%%".format(efiRealFeePercent)
            val efiFeeRealFmt = "R$ %.2f".format(efiFeeReal.toDouble())
            val installmentsSuffix = installmentsInfo?.let { " - $it" } ?: ""
            
            """
                <tr style="background:#fff;">
                  <td style="color:#6c757d;padding:6px 8px;text-align:left;border-top:1px solid #dee2e6;">
                    Taxa Efí Bank $paymentTypeLabel ($efiFeePercentFmt = $efiFeeRealFmt)$installmentsSuffix:
                  </td>
                  <td style="color:#dc3545;font-weight:600;padding:6px 8px;text-align:right;border-top:1px solid #dee2e6;">-$efiFeeRealFmt</td>
                </tr>
            """.trimIndent()
        } else {
            ""
        }
        
        // Formata a descrição da margem
        val marginLabel = if (marginFixed > 0) {
            "Margem (${marginPercentFmt} + R$ ${marginFixed}):"
        } else {
            "Margem (${marginPercentFmt}):"
        }
        
        return """
            <!-- DETALHAMENTO DE DESCONTO -->
            <div style="background:#f8f9fa;border:1px solid #dee2e6;border-radius:8px;padding:20px;margin:16px 0;">
              <div style="font-weight:700;color:#495057;font-size:16px;margin-bottom:12px;text-align:center;">
                💰 Detalhamento do Repasse
              </div>
              <table width="100%" cellspacing="0" cellpadding="8" style="border-collapse:collapse;font-size:14px;">
                <tr>
                  <td style="color:#6c757d;padding:6px 8px;text-align:left;">Valor bruto do pedido:</td>
                  <td style="font-weight:600;color:#212529;padding:6px 8px;text-align:right;">$grossFmt</td>
                </tr>
                $efiFeeLine
                <tr>
                  <td style="color:#6c757d;padding:6px 8px;text-align:left;border-top:1px solid #dee2e6;">
                    $marginLabel
                  </td>
                  <td style="color:#dc3545;font-weight:600;padding:6px 8px;text-align:right;border-top:1px solid #dee2e6;">-$marginFmt</td>
                </tr>
                <tr style="background:#fff3cd;">
                  <td style="font-weight:700;color:#856404;padding:8px;text-align:left;border-top:2px solid #ffc107;">
                    Custos de Operação:
                  </td>
                  <td style="font-weight:700;color:#dc3545;padding:8px;text-align:right;border-top:2px solid #ffc107;">-$discountFmt</td>
                </tr>
                <tr style="background:#d4edda;">
                  <td style="font-weight:700;color:#155724;padding:10px 8px;text-align:left;border-top:2px solid #28a745;font-size:16px;">
                    Valor líquido repassado:
                  </td>
                  <td style="font-weight:700;color:#155724;padding:10px 8px;text-align:right;border-top:2px solid #28a745;font-size:18px;">
                    $netFmt
                  </td>
                </tr>
              </table>
            </div>
        """.trimIndent()
    }
}

