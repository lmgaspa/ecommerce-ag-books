// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/PaymentTriggerService.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

data class PayoutResult(
    val orderId: Long?,
    val status: String,                // SUCCESS | FAILED | ERROR
    val message: String? = null,
    val amountGross: BigDecimal? = null,
    val amountNet: BigDecimal? = null,
    val minSend: BigDecimal? = null,
    val pixKey: String? = null,
    val providerRef: String? = null
)

/**
 * Regras de orquestração do repasse (payout) 1:1 por pedido.
 * - Aberto para extensão por:
 *   - PixPayoutProvider (injeção de estratégia de envio)
 *   - Resolução de chave favorecida via override por chamada
 *   - Descoberta dinâmica de colunas de total do pedido
 */
@Service
class PaymentTriggerService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val payoutProps: EfiPayoutProps,
    private val pixProvider: PixPayoutProvider,
    private val env: Environment
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ABS_MIN = BigDecimal("1.20") // piso líquido mínimo

    /**
     * Dispara (ou atualiza) o payout 1:1 do pedido.
     * @param orderRef        id do pedido em string (ex.: "481")
     * @param externalId      txid/referência externa (para log/correlação)
     * @param sourceProvider  origem (ex.: "PIX-POLLER" | "PIX-WEBHOOK" | "MANUAL")
     * @param overridePixKey  opcional: força o favorecido desta chamada (não global)
     */
    fun tryTriggerByRef(
        orderRef: String?,
        externalId: String,
        sourceProvider: String,
        overridePixKey: String? = null
    ): PayoutResult {
        val orderId = orderRef?.toLongOrNull()
        if (orderId == null) {
            val msg = "orderRef inválido ou nulo; tx=$externalId"
            log.warn(msg)
            return PayoutResult(orderId, "ERROR", msg)
        }

        // 1) Totais do pedido (itens + frete)
        val totals = runCatching { fetchOrderTotals(orderId) }.getOrElse {
            val msg = "Erro lendo totais do pedido: ${it.message}"
            log.error(msg)
            return PayoutResult(orderId, "ERROR", msg)
        } ?: return PayoutResult(orderId, "ERROR", "Pedido não encontrado")
        val amountGross = totals.amountGross.max(BigDecimal.ZERO)

        // 2) Parâmetros de cálculo
        val includeGatewayFees = payoutProps.fees.includeGatewayFees
        val feePercent    = bd(payoutProps.feePercent)
        val feeFixed      = bd(payoutProps.feeFixed)
        val marginPercent = bd(payoutProps.marginPercent)
        val marginFixed   = bd(payoutProps.marginFixed)
        val configuredMin = bd(payoutProps.minSend)
        val effectiveMin  = configuredMin.max(ABS_MIN)

        // 3) Cálculo líquido
        val amountNet = calcNet(amountGross, includeGatewayFees, feePercent, feeFixed, marginPercent, marginFixed)

        // 4) Chave PIX do favorecido (override → props → autor)
        val favoredKey = resolveFavoredKey(overridePixKey)
        if (favoredKey.isBlank()) {
            val msg = "Sem EFI_PAYOUT_FAVORED_KEY e sem autor ativo com pix_key — repasse abortado"
            log.warn(msg)
            return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, null, null)
        }

        // 5) Upsert CREATED (idempotente por order_id)
        runCatching {
            upsertCreated(
                orderId = orderId,
                amountGross = amountGross,
                amountNet = amountNet,
                includeGatewayFees = includeGatewayFees,
                feePercent = feePercent,
                feeFixed = feeFixed,
                marginPercent = marginPercent,
                marginFixed = marginFixed,
                minSend = effectiveMin,
                pixKey = favoredKey
            )
        }.onFailure {
            val msg = "Falha ao registrar payout CREATED: ${it.message}"
            log.error(msg)
            return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, favoredKey, null)
        }

        // 6) Regra de mínimo
        if (amountNet < effectiveMin) {
            val reason = "Valor líquido ${fmt(amountNet)} abaixo do mínimo ${fmt(effectiveMin)}"
            runCatching { markFailed(orderId, reason) }
            log.warn("Payout FAILED order #{}: {}", orderId, reason)
            return PayoutResult(orderId, "FAILED", reason, amountGross, amountNet, effectiveMin, favoredKey, null)
        }

        // 7) Envio PIX (provider) → marca SENT; CONFIRMED vem via webhook (exceto profile stub)
        val sendResult = runCatching { pixProvider.sendPixPayout(orderId, amountNet, favoredKey) }
        if (sendResult.isFailure) {
            val errMsg = sendResult.exceptionOrNull()?.message ?: "erro desconhecido no provider"
            runCatching { markFailed(orderId, "Envio PIX falhou: $errMsg") }
            log.error("Falha ao enviar payout order #{}: {}", orderId, errMsg)
            return PayoutResult(orderId, "FAILED", "Envio PIX falhou: $errMsg", amountGross, amountNet, effectiveMin, favoredKey, null)
        }
        val ref = sendResult.getOrThrow()

        runCatching { markSent(orderId, ref) }.onFailure {
            val msg = "Falha ao marcar SENT: ${it.message}"
            log.error(msg)
            return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, favoredKey, ref)
        }

        if (isStubProfile()) {
            // Em dev/local (stub), confirma de imediato
            runCatching { markConfirmed(orderId) }.onFailure {
                val msg = "Falha ao marcar CONFIRMED (stub): ${it.message}"
                log.error(msg)
                return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, favoredKey, ref)
            }
            log.info(
                "Payout CONFIRMED (stub) order #{} providerRef={} src={} gross={} net={} key={}",
                orderId, ref, sourceProvider, fmt(amountGross), fmt(amountNet), maskKey(favoredKey)
            )
            return PayoutResult(orderId, "SUCCESS", null, amountGross, amountNet, effectiveMin, favoredKey, ref)
        }

        log.info(
            "Payout SENT order #{} providerRef={} src={} gross={} net={} key={}",
            orderId, ref, sourceProvider, fmt(amountGross), fmt(amountNet), maskKey(favoredKey)
        )
        // Em prod, status final vem do webhook de envio (REALIZADO/NAO_REALIZADO)
        return PayoutResult(
            orderId, "SUCCESS",
            "Enviado ao provedor; aguardando confirmação via webhook",
            amountGross, amountNet, effectiveMin, favoredKey, ref
        )
    }

    // ===== consulta dinâmica dos totais (itens + frete) =====
    private data class OrderTotals(val amountGross: BigDecimal)

    private fun fetchOrderTotals(orderId: Long): OrderTotals? {
        val cols: Set<String> = jdbc.query(
            """
            SELECT column_name FROM information_schema.columns
             WHERE table_name = 'orders' AND table_schema = current_schema()
            """.trimIndent(),
            emptyMap<String, Any>()
        ) { rs, _ -> rs.getString("column_name").lowercase() }.toSet()

        fun has(name: String) = cols.contains(name.lowercase())

        val itemCandidates = listOf("items_total", "subtotal", "total_items", "amount_items", "itemsamount", "valor_itens", "valor_total")
        val shipCandidates = listOf("shipping_total", "freight_total", "frete_total", "frete", "shipping", "valor_frete")

        val itemCol = itemCandidates.firstOrNull { has(it) }
        val shipCol = shipCandidates.firstOrNull { has(it) }

        val sql = when {
            itemCol != null && shipCol != null ->
                "SELECT ($itemCol + $shipCol) AS amount_gross FROM orders WHERE id = :id"
            itemCol != null ->
                "SELECT $itemCol AS amount_gross FROM orders WHERE id = :id"
            has("grand_total") ->
                "SELECT grand_total AS amount_gross FROM orders WHERE id = :id"
            has("total") ->
                "SELECT total AS amount_gross FROM orders WHERE id = :id"
            else -> {
                log.warn("Não encontrei colunas conhecidas em orders; usando 0 como amount_gross (orderId={})", orderId)
                "SELECT 0::numeric(12,2) AS amount_gross"
            }
        }

        return jdbc.query(sql, mapOf("id" to orderId)) { rs, _ ->
            OrderTotals(rs.getBigDecimal("amount_gross") ?: BigDecimal.ZERO)
        }.firstOrNull()
    }

    // ===== persistência payout (compatível com coluna legacy 'amount' NOT NULL) =====
    private fun tableHasColumn(table: String, column: String): Boolean =
        jdbc.queryForList(
            """
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND table_name = :t
               AND column_name = :c
            LIMIT 1
            """.trimIndent(),
            mapOf("t" to table, "c" to column),
            Int::class.java
        ).isNotEmpty()

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
        val hasLegacyAmount = tableHasColumn("payment_payouts", "amount") // coluna legado NOT NULL

        val params = mutableMapOf<String, Any>(
            "id"  to orderId,
            "ag"  to amountGross.setScale(2, RoundingMode.HALF_UP),
            "an"  to amountNet.setScale(2, RoundingMode.HALF_UP),
            "inc" to includeGatewayFees,
            "fp"  to feePercent,
            "ff"  to feeFixed.setScale(2, RoundingMode.HALF_UP),
            "mp"  to marginPercent,
            "mf"  to marginFixed.setScale(2, RoundingMode.HALF_UP),
            "ms"  to minSend.setScale(2, RoundingMode.HALF_UP),
            "pix" to pixKey
        )
        if (hasLegacyAmount) params["amt"] = amountNet.setScale(2, RoundingMode.HALF_UP)

        val sql =
            if (hasLegacyAmount) {
                """
                INSERT INTO payment_payouts(
                    order_id, status, amount, amount_gross, amount_net, include_gateway_fees,
                    fee_percent, fee_fixed, margin_percent, margin_fixed, min_send, pix_key, created_at
                )
                VALUES (:id,'CREATED',:amt,:ag,:an,:inc,:fp,:ff,:mp,:mf,:ms,:pix, NOW())
                ON CONFLICT (order_id) DO UPDATE
                  SET status='CREATED',
                      amount       = EXCLUDED.amount,
                      amount_gross = EXCLUDED.amount_gross,
                      amount_net   = EXCLUDED.amount_net,
                      include_gateway_fees = EXCLUDED.include_gateway_fees,
                      fee_percent  = EXCLUDED.fee_percent,
                      fee_fixed    = EXCLUDED.fee_fixed,
                      margin_percent = EXCLUDED.margin_percent,
                      margin_fixed   = EXCLUDED.margin_fixed,
                      min_send       = EXCLUDED.min_send,
                      pix_key        = EXCLUDED.pix_key
                """.trimIndent()
            } else {
                """
                INSERT INTO payment_payouts(
                    order_id, status, amount_gross, amount_net, include_gateway_fees,
                    fee_percent, fee_fixed, margin_percent, margin_fixed, min_send, pix_key, created_at
                )
                VALUES (:id,'CREATED',:ag,:an,:inc,:fp,:ff,:mp,:mf,:ms,:pix, NOW())
                ON CONFLICT (order_id) DO UPDATE
                  SET status='CREATED',
                      amount_gross = EXCLUDED.amount_gross,
                      amount_net   = EXCLUDED.amount_net,
                      include_gateway_fees = EXCLUDED.include_gateway_fees,
                      fee_percent  = EXCLUDED.fee_percent,
                      fee_fixed    = EXCLUDED.fee_fixed,
                      margin_percent = EXCLUDED.margin_percent,
                      margin_fixed   = EXCLUDED.margin_fixed,
                      min_send       = EXCLUDED.min_send,
                      pix_key        = EXCLUDED.pix_key
                """.trimIndent()
            }

        jdbc.update(sql, params)
    }

    private fun markSent(orderId: Long, providerRef: String) {
        jdbc.update(
            """UPDATE payment_payouts SET status='SENT', provider_ref=:ref, sent_at=NOW() WHERE order_id=:id""",
            mapOf("id" to orderId, "ref" to providerRef)
        )
    }

    private fun markConfirmed(orderId: Long) {
        jdbc.update(
            """UPDATE payment_payouts SET status='CONFIRMED', confirmed_at=NOW() WHERE order_id=:id""",
            mapOf("id" to orderId)
        )
    }

    private fun markFailed(orderId: Long, reason: String) {
        jdbc.update(
            """UPDATE payment_payouts SET status='FAILED', fail_reason=:r, failed_at=NOW() WHERE order_id=:id""",
            mapOf("id" to orderId, "r" to reason)
        )
    }

    // ===== helpers =====

    /** Resolve chave favorecida com precedência: override → props → autor ativo. */
    private fun resolveFavoredKey(overridePixKey: String?): String =
        overridePixKey?.takeIf { it.isNotBlank() }
            ?: payoutProps.favoredKey?.takeIf { it.isNotBlank() }
            ?: fetchAuthorPixKey().orEmpty()

    private fun fetchAuthorPixKey(): String? =
        jdbc.queryForList(
            "SELECT pix_key FROM payment_site_author WHERE active=true LIMIT 1",
            emptyMap<String, Any>(), String::class.java
        ).firstOrNull()

    private fun bd(d: Double) = BigDecimal.valueOf(d)
    private fun fmt(v: BigDecimal) = "R$ " + v.setScale(2, RoundingMode.HALF_UP).toPlainString()
    private fun maskKey(k: String) = if (k.length <= 6) "***" else k.take(3) + "***" + k.takeLast(3)
    private fun isStubProfile(): Boolean =
        env.activeProfiles.any { it.equals("stub", ignoreCase = true) }

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

        var net = amountGross.minus(fee).minus(margin).setScale(2, RoundingMode.HALF_UP)
        // ⚖️ Garantia formal de negócio: repasse sempre menor que recebido.
        if (net >= amountGross) {
            net = amountGross.minus(BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP)
        }
        return net
    }
}
