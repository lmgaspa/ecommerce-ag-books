package com.luizgasparetto.backend.monolito.payments.web

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

/** OCP: provider injeta envio/consulta; service faz orquestração & persistência. */
@Service
class PaymentTriggerService(
    private val jdbc: NamedParameterJdbcTemplate,
    private val payoutProps: EfiPayoutProps,
    private val pixProvider: PixPayoutProvider,
    private val env: Environment
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ABS_MIN = BigDecimal("1.20") // piso líquido mínimo

    @Transactional
    fun tryTriggerByRef(
        orderRef: String?,
        externalId: String,
        sourceProvider: String,
        overridePixKey: String? = null
    ): PayoutResult {
        val orderId = orderRef?.toLongOrNull()
        if (orderId == null) {
            val msg = "orderRef inválido ou nulo; tx=$externalId"
            log.warn("PAYOUT TRIGGER: {}", msg)
            return PayoutResult(orderId, "ERROR", msg)
        }

        // 1) Totais do pedido
        val totals = runCatching { fetchOrderTotals(orderId) }.getOrElse {
            val msg = "Erro lendo totais do pedido: ${it.message}"
            log.error("PAYOUT TRIGGER: {}", msg)
            return PayoutResult(orderId, "ERROR", msg)
        } ?: return PayoutResult(orderId, "ERROR", "Pedido não encontrado")
        val amountGross = totals.amountGross.max(BigDecimal.ZERO)
        log.info("PAYOUT TRIGGER: order #{} amountGross={}", orderId, fmt(amountGross))

        // 2) Parâmetros
        val includeGatewayFees = payoutProps.fees.includeGatewayFees
        val feePercent    = bd(payoutProps.feePercent)
        val feeFixed      = bd(payoutProps.feeFixed)
        val marginPercent = bd(payoutProps.marginPercent)
        val marginFixed   = bd(payoutProps.marginFixed)
        val configuredMin = bd(payoutProps.minSend)
        val effectiveMin  = configuredMin.max(ABS_MIN)
        log.info(
            "PAYOUT TRIGGER: params incFees={} fee%={} feeFix={} margin%={} marginFix={} minConf={} minEff={}",
            includeGatewayFees, feePercent, feeFixed, marginPercent, marginFixed,
            fmt(configuredMin), fmt(effectiveMin)
        )

        // 3) Líquido
        val amountNet = calcNet(amountGross, includeGatewayFees, feePercent, feeFixed, marginPercent, marginFixed)
        log.info("PAYOUT TRIGGER: order #{} amountNet={} (gross={})", orderId, fmt(amountNet), fmt(amountGross))

        // 4) Favorecido
        val favoredKey = resolveFavoredKey(overridePixKey)
        if (favoredKey.isBlank()) {
            val msg = "Sem EFI_PAYOUT_FAVORED_KEY e sem autor ativo com pix_key — repasse abortado"
            log.warn("PAYOUT TRIGGER: {}", msg)
            return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, null, null)
        }
        log.info("PAYOUT TRIGGER: order #{} favoredKey={}", orderId, maskKey(favoredKey))

        // 5) UPSERT CREATED (idempotente, sem rebaixar SENT/CONFIRMED)
        runCatching {
            upsertCreated(
                orderId, amountGross, amountNet, includeGatewayFees,
                feePercent, feeFixed, marginPercent, marginFixed, effectiveMin, favoredKey
            )
        }.onFailure {
            val msg = "Falha ao registrar payout CREATED: ${it.message}"
            log.error("PAYOUT TRIGGER: {}", msg)
            return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, favoredKey, null)
        }

        // 6) Mínimo
        if (amountNet < effectiveMin) {
            val reason = "Valor líquido ${fmt(amountNet)} abaixo do mínimo ${fmt(effectiveMin)}"
            runCatching { markFailed(orderId, reason) }.onFailure { e ->
                log.warn("PAYOUT TRIGGER: falha ao marcar FAILED por mínimo: {}", e.message)
            }
            log.warn("Payout FAILED order #{}: {}", orderId, reason)
            return PayoutResult(orderId, "FAILED", reason, amountGross, amountNet, effectiveMin, favoredKey, null)
        }

        // 7) Envio (provider) → SENT
        val sendResult = runCatching {
            log.info("PAYOUT TRIGGER: enviando PIX order #{} net={} key={} src={}", orderId, fmt(amountNet), maskKey(favoredKey), sourceProvider)
            pixProvider.sendPixPayout(orderId, amountNet, favoredKey) // retorna idEnvio (Efí) ou ref real do provedor
        }
        if (sendResult.isFailure) {
            val errMsg = sendResult.exceptionOrNull()?.message ?: "erro desconhecido no provider"
            runCatching { markFailed(orderId, "Envio PIX falhou: $errMsg") }
            log.error("PAYOUT TRIGGER: Falha ao enviar payout order #{}: {}", orderId, errMsg)
            return PayoutResult(orderId, "FAILED", "Envio PIX falhou: $errMsg", amountGross, amountNet, effectiveMin, favoredKey, null)
        }
        val providerRef = sendResult.getOrThrow()

        // ✅ Aceita idEnvio padrão da Efí (ex.: "P634"): apenas valide formato alfanumérico (1..35)
        if (!providerRef.matches(Regex("^[A-Za-z0-9]{1,35}$"))) {
            val reason = "providerRef '$providerRef' possui formato inválido (esperado: alfanumérico até 35 chars)"
            runCatching { markFailed(orderId, reason) }
            log.warn("PAYOUT TRIGGER: {}", reason)
            return PayoutResult(orderId, "FAILED", reason, amountGross, amountNet, effectiveMin, favoredKey, null)
        }

        runCatching { markSent(orderId, providerRef) }.onFailure {
            val msg = "Falha ao marcar SENT: ${it.message}"
            log.error("PAYOUT TRIGGER: {}", msg)
            return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, favoredKey, providerRef)
        }

        if (isStubProfile()) {
            // stub → confirma já
            runCatching { markConfirmed(orderId) }.onFailure {
                val msg = "Falha ao marcar CONFIRMED (stub): ${it.message}"
                log.error("PAYOUT TRIGGER: {}", msg)
                return PayoutResult(orderId, "ERROR", msg, amountGross, amountNet, effectiveMin, favoredKey, providerRef)
            }
            log.info(
                "Payout CONFIRMED (stub) order #{} providerRef={} src={} gross={} net={} key={}",
                orderId, providerRef, sourceProvider, fmt(amountGross), fmt(amountNet), maskKey(favoredKey)
            )
            return PayoutResult(orderId, "SUCCESS", null, amountGross, amountNet, effectiveMin, favoredKey, providerRef)
        }

        log.info(
            "Payout SENT order #{} providerRef={} src={} gross={} net={} key={}",
            orderId, providerRef, sourceProvider, fmt(amountGross), fmt(amountNet), maskKey(favoredKey)
        )
        return PayoutResult(
            orderId, "SUCCESS",
            "Enviado ao provedor; aguardando confirmação", amountGross, amountNet, effectiveMin, favoredKey, providerRef
        )
    }

    // ===== consulta dinâmica dos totais (itens + frete)
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

        val itemCandidates = listOf("items_total","subtotal","total_items","amount_items","itemsamount","valor_itens","valor_total")
        val shipCandidates = listOf("shipping_total","freight_total","frete_total","frete","shipping","valor_frete")

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
                log.warn("PAYOUT TRIGGER: colunas não reconhecidas em orders; usando 0 como amount_gross (orderId={})", orderId)
                "SELECT 0::numeric(12,2) AS amount_gross"
            }
        }

        return jdbc.query(sql, mapOf("id" to orderId)) { rs, _ ->
            OrderTotals(rs.getBigDecimal("amount_gross") ?: BigDecimal.ZERO)
        }.firstOrNull()
    }

    // ===== introspecção e persistência
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
        val hasLegacyAmount = tableHasColumn("payment_payouts", "amount")

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

        val whereGuard = "WHERE payment_payouts.status NOT IN ('SENT','CONFIRMED')"

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
                      amount         = EXCLUDED.amount,
                      amount_gross   = EXCLUDED.amount_gross,
                      amount_net     = EXCLUDED.amount_net,
                      include_gateway_fees = EXCLUDED.include_gateway_fees,
                      fee_percent    = EXCLUDED.fee_percent,
                      fee_fixed      = EXCLUDED.fee_fixed,
                      margin_percent = EXCLUDED.margin_percent,
                      margin_fixed   = EXCLUDED.margin_fixed,
                      min_send       = EXCLUDED.min_send,
                      pix_key        = EXCLUDED.pix_key
                $whereGuard
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
                      amount_gross   = EXCLUDED.amount_gross,
                      amount_net     = EXCLUDED.amount_net,
                      include_gateway_fees = EXCLUDED.include_gateway_fees,
                      fee_percent    = EXCLUDED.fee_percent,
                      fee_fixed      = EXCLUDED.fee_fixed,
                      margin_percent = EXCLUDED.margin_percent,
                      margin_fixed   = EXCLUDED.margin_fixed,
                      min_send       = EXCLUDED.min_send,
                      pix_key        = EXCLUDED.pix_key
                $whereGuard
                """.trimIndent()
            }

        val rows = jdbc.update(sql, params)
        log.debug("PAYOUT TRIGGER: upsertCreated order #{} rows={}", orderId, rows)
    }

    private fun markSent(orderId: Long, providerRef: String) {
        val rows = jdbc.update(
            """UPDATE payment_payouts
                 SET status='SENT', provider_ref=:ref, sent_at=NOW()
               WHERE order_id=:id
                 AND status IN ('CREATED','FAILED')""",
            mapOf("id" to orderId, "ref" to providerRef)
        )
        log.info("PAYOUT TRIGGER: markSent order #{} ref={} rows={}", orderId, providerRef, rows)
    }

    private fun markConfirmed(orderId: Long) {
        val rows = jdbc.update(
            """UPDATE payment_payouts
                 SET status='CONFIRMED', confirmed_at=NOW()
               WHERE order_id=:id
                 AND status IN ('SENT','CREATED','FAILED')""",
            mapOf("id" to orderId)
        )
        log.info("PAYOUT TRIGGER: markConfirmed order #{} rows={}", orderId, rows)
    }

    private fun markFailed(orderId: Long, reason: String) {
        val rows = jdbc.update(
            """UPDATE payment_payouts
                 SET status='FAILED', fail_reason=:r, failed_at=NOW()
               WHERE order_id=:id
                 AND status <> 'CONFIRMED'""",
            mapOf("id" to orderId, "r" to reason)
        )
        log.warn("PAYOUT TRIGGER: markFailed order #{} reason='{}' rows={}", orderId, reason, rows)
    }

    // ===== helpers =====

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
    private fun isStubProfile(): Boolean = env.activeProfiles.any { it.equals("stub", ignoreCase = true) }

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
        if (net >= amountGross) net = amountGross.minus(BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP)
        return net
    }
}
