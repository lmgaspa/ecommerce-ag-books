// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/scheduling/CardPayoutScheduler.kt
package com.luizgasparetto.backend.monolito.payments.scheduling

import com.luizgasparetto.backend.monolito.payments.web.PaymentTriggerService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * Dispara repasses (payout) de pedidos pagos via CARTÃO respeitando janela D+N.
 * Mantém OCP/SOLID: a regra de cálculo, mínimos e envio fica toda no PaymentTriggerService.
 */
@Component
class CardPayoutScheduler(
    private val jdbc: NamedParameterJdbcTemplate,
    private val payoutTrigger: PaymentTriggerService,
    @Value("\${payout.delay.cardDays:32}") private val cardDays: Int,
    @Value("\${payout.scheduler.card.batchSize:100}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * CRON configurável (default: 03:17 diariamente).
     * Use timezone da JVM. Se quiser forçar: @Scheduled(cron = "...", zone = "America/Bahia")
     */
    @Scheduled(cron = "\${payout.scheduler.card.cron:0 17 3 * * *}")
    fun runDailyBatch() {
        val startedAt = OffsetDateTime.now()
        log.info("CARD-PAYOUT-SCHED start at={} cardDays={} batchSize={}", startedAt, cardDays, batchSize)

        // Seleciona pedidos:
        // - pagos (paid=true)
        // - com charge_id (indicativo de cartão)
        // - pagos há >= cardDays
        // - ainda sem qualquer registro em payment_payouts (evita duplicidade)
        val sql = """
            SELECT o.id AS order_id, o.charge_id
              FROM orders o
             WHERE o.paid = true
               AND o.charge_id IS NOT NULL
               AND o.paid_at <= NOW() - make_interval(days => :days)
               AND NOT EXISTS (
                     SELECT 1 FROM payment_payouts p
                      WHERE p.order_id = o.id
               )
             ORDER BY o.paid_at ASC
             LIMIT :lim
        """.trimIndent()

        val toProcess = jdbc.query(sql, mapOf("days" to cardDays, "lim" to batchSize)) { rs, _ ->
            Candidate(
                orderId = rs.getLong("order_id"),
                chargeId = rs.getString("charge_id")
            )
        }

        if (toProcess.isEmpty()) {
            log.info("CARD-PAYOUT-SCHED nothing to do (0 candidates)")
            return
        }

        var ok = 0
        var fail = 0

        toProcess.forEach { c ->
            val extId = c.chargeId?.takeIf { it.isNotBlank() } ?: "CARD-SCHEDULED"
            runCatching {
                payoutTrigger.tryTriggerByRef(
                    orderRef = c.orderId.toString(),
                    externalId = extId,
                    sourceProvider = "CARD-SCHEDULED"
                )
            }.onSuccess {
                ok++
                log.info(
                    "CARD-PAYOUT-SCHED triggered orderId={} externalId={} resultStatus={}",
                    c.orderId, extId, it.status
                )
            }.onFailure { e ->
                fail++
                log.error(
                    "CARD-PAYOUT-SCHED trigger FAILED orderId={} externalId={} err={}",
                    c.orderId, extId, e.message, e
                )
            }
        }

        log.info(
            "CARD-PAYOUT-SCHED done at={} processed={} ok={} fail={}",
            OffsetDateTime.now(), toProcess.size, ok, fail
        )
    }

    private data class Candidate(
        val orderId: Long,
        val chargeId: String?
    )
}
