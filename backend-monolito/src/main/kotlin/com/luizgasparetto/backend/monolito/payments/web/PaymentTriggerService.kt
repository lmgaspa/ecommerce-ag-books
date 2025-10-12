// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/PaymentTriggerService.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PaymentTriggerService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val payoutProps: EfiPayoutProps,
    private val provider: PixPayoutProvider
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ABS_MIN = BigDecimal("1.20") // piso regulatório/operacional

    fun tryTriggerByRef(orderRef: String?, externalId: String, provider: String) {
        val orderId = orderRef?.toLongOrNull()
            ?: error("orderRef inválido ou nulo; não é possível disparar payout (tx=$externalId)")

        val order = fetchOrder(orderId) ?: run {
            log.error("Pedido #{} não encontrado para repasse", orderId)
            return
        }

        // ✅ Frete incluso no repasse (itens + frete)
        val amountGross = (order.itemsTotal + order.shippingTotal).max(BigDecimal.ZERO)

        val includeGatewayFees = payoutProps.fees.includeGatewayFees
        val feePercent = bd(payoutProps.feePercent)
        val feeFixed = bd(payoutProps.feeFixed)
        val marginPercent = bd(payoutProps.marginPercent)
        val marginFixed = bd(payoutProps.marginFixed)
        val configuredMin = bd(payoutProps.minSend)

        // ✅ Piso efetivo = max(config, 1.20)
        val effectiveMin = configuredMin.max(ABS_MIN)

        val net = calcNet(amountGross, includeGatewayFees, feePercent, feeFixed, marginPercent, marginFixed)

        // chave do favorecido: env 'favored-key' OU autor ativo
        val favoredKey: String = payoutProps.favoredKey?.takeIf { it.isNotBlank() }
            ?: fetchAuthorPixKey()
            ?: error("Sem EFI_PAYOUT_FAVORED_KEY e sem autor ativo com pix_key — não posso repassar")

        // Upsert payout CREATED
        upsertCreated(
            orderId = orderId,
            amountGross = amountGross,
            amountNet = net,
            includeGatewayFees = includeGatewayFees,
            feePercent = feePercent,
            feeFixed = feeFixed,
            marginPercent = marginPercent,
            marginFixed = marginFixed,
            minSend = effectiveMin,
            pixKey = favoredKey
        )

        if (net < effectiveMin) {
            val reason = "Valor líquido ${fmt(net)} abaixo do mínimo ${fmt(effectiveMin)}"
            markFailed(orderId, reason)
            log.warn("Payout FAILED order #{}: {}", orderId, reason)
            return
        }

        try {
            val ref = this.provider.sendPixPayout(orderId, net, favoredKey)
            markSent(orderId, ref)
            markConfirmed(orderId)
            log.info("Payout CONFIRMED order #{} providerRef={}", orderId, ref)
        } catch (e: Exception) {
            markFailed(orderId, e.message ?: "Falha no envio PIX")
            log.error("Falha ao enviar payout order #{}: {}", orderId, e.message)
        }
    }

    // --- persistência / consultas ---

    private data class OrderRow(val itemsTotal: BigDecimal, val shippingTotal: BigDecimal)

    private fun fetchOrder(orderId: Long): OrderRow? =
        jdbc.query(
            """
            SELECT items_total, shipping_total
              FROM orders
             WHERE id = :id
            """.trimIndent(),
            mapOf("id" to orderId)
        ) { rs, _ ->
            OrderRow(
                itemsTotal = rs.getBigDecimal("items_total"),
                shippingTotal = rs.getBigDecimal("shipping_total")
            )
        }.firstOrNull()

    private fun fetchAuthorPixKey(): String? =
        jdbc.queryForList(
            "SELECT pix_key FROM payment_site_author WHERE active=true LIMIT 1",
            emptyMap<String, Any>(),
            String::class.java
        ).firstOrNull()

    private fun upsertCreated(
        orderId: Long,
        amountGross: BigDecimal,
        amountNet: BigDecimal,
        includeGatewayFees: Boolean,
        feePercent: BigDecimal,
        feeFixed: BigDecimal,
        marginPercent: BigDecimal,
        marginFixed: BigDecimal,
        minSend: BigDecimal,
        pixKey: String
    ) {
        jdbc.update(
            """
            INSERT INTO payment_payouts(order_id,status,amount_gross,amount_net,include_gateway_fees,
                                        fee_percent,fee_fixed,margin_percent,margin_fixed,min_send,pix_key,created_at)
            VALUES (:id,'CREATED',:ag,:an,:inc,:fp,:ff,:mp,:mf,:ms,:pix, NOW())
            ON CONFLICT (order_id) DO UPDATE
              SET status='CREATED',
                  amount_gross=EXCLUDED.amount_gross,
                  amount_net=EXCLUDED.amount_net,
                  include_gateway_fees=EXCLUDED.include_gateway_fees,
                  fee_percent=EXCLUDED.fee_percent,
                  fee_fixed=EXCLUDED.fee_fixed,
                  margin_percent=EXCLUDED.margin_percent,
                  margin_fixed=EXCLUDED.margin_fixed,
                  min_send=EXCLUDED.min_send,
                  pix_key=EXCLUDED.pix_key
            """.trimIndent(),
            mapOf(
                "id" to orderId, "ag" to amountGross, "an" to net2(amountNet),
                "inc" to includeGatewayFees, "fp" to feePercent, "ff" to feeFixed,
                "mp" to marginPercent, "mf" to marginFixed, "ms" to minSend, "pix" to pixKey
            )
        )
    }

    private fun markSent(orderId: Long, providerRef: String) {
        jdbc.update(
            """UPDATE payment_payouts
               SET status='SENT', provider_ref=:ref, sent_at=NOW()
             WHERE order_id=:id""".trimIndent(),
            mapOf("id" to orderId, "ref" to providerRef)
        )
    }

    private fun markConfirmed(orderId: Long) {
        jdbc.update(
            """UPDATE payment_payouts
               SET status='CONFIRMED', confirmed_at=NOW()
             WHERE order_id=:id""".trimIndent(),
            mapOf("id" to orderId)
        )
    }

    private fun markFailed(orderId: Long, reason: String) {
        jdbc.update(
            """UPDATE payment_payouts
               SET status='FAILED', fail_reason=:r, failed_at=NOW()
             WHERE order_id=:id""".trimIndent(),
            mapOf("id" to orderId, "r" to reason)
        )
    }

    // --- cálculo / helpers ---

    private fun bd(d: Double) = BigDecimal.valueOf(d)
    private fun net2(v: BigDecimal) = v.setScale(2, RoundingMode.HALF_UP)
    private fun fmt(v: BigDecimal) = "R$ " + net2(v).toPlainString()

    private fun calcNet(
        amountGross: BigDecimal,
        includeGatewayFees: Boolean,
        feePercent: BigDecimal,
        feeFixed: BigDecimal,
        marginPercent: BigDecimal,
        marginFixed: BigDecimal
    ): BigDecimal {
        val hundred = BigDecimal("100")
        val fee = if (includeGatewayFees)
            amountGross.multiply(feePercent).divide(hundred, 2, RoundingMode.HALF_UP).plus(feeFixed)
        else BigDecimal.ZERO
        val margin = amountGross.multiply(marginPercent).divide(hundred, 2, RoundingMode.HALF_UP).plus(marginFixed)
        return amountGross.minus(fee).minus(margin).setScale(2, RoundingMode.HALF_UP)
    }
}
