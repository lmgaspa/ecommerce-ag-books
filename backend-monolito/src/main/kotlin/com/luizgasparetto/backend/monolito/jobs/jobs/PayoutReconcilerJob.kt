package com.luizgasparetto.backend.monolito.payments.jobs

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime

@Component
class PayoutReconcilerJob(
    private val jdbc: NamedParameterJdbcTemplate,
    private val ctx: ApplicationContext,
    @Value("\${payout.scheduler.reconciler.minSentAgeSeconds:60}")
    private val minAgeSeconds: Long,
    @Value("\${payout.scheduler.reconciler.batchSize:50}")
    private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Row(
        val orderId: Long,
        val providerRef: String?,
        val sentAt: OffsetDateTime,
        val amountNet: String,
        val pixMask: String
    )

    @Scheduled(fixedDelayString = "\${payout.scheduler.reconciler.fixedDelayMs:60000}")
    fun run() {
        log.info("RECONCILE: start (minAge={}s, batch={})", minAgeSeconds, batchSize)

        val rows = fetchSentOlderThan(minAgeSeconds, batchSize)
        if (rows.isEmpty()) {
            log.info("RECONCILE: nenhum payout para verificar (minAge={}s).", minAgeSeconds)
            return
        }
        log.info("RECONCILE: {} payout(s) para verificar (minAge={}s, batch={})", rows.size, minAgeSeconds, batchSize)

        rows.forEach { r ->
            val ref = r.providerRef
            if (ref.isNullOrBlank() || isLocalLookingRef(ref)) {
                log.warn(
                    "RECONCILE: ignorando provider_ref inválido/suspeito (orderId={}, ref={}, sentAt={}, net={}, keyMask={})",
                    r.orderId, ref, r.sentAt, r.amountNet, r.pixMask
                )
                return@forEach
            }

            val ageMin = Duration.between(r.sentAt, OffsetDateTime.now()).toMinutes()
            val status = runCatching { checkStatusViaProvider(ref) }
                .onFailure { e -> log.warn("RECONCILE: falha consultando ref={} order #{}: {}", ref, r.orderId, e.message) }
                .getOrElse { "UNKNOWN" }

            when (status) {
                "CONFIRMED" -> {
                    val rowsU = jdbc.update(
                        """
                        UPDATE payment_payouts
                           SET status='CONFIRMED', confirmed_at=NOW()
                         WHERE order_id=:id AND status='SENT'
                        """.trimIndent(),
                        mapOf<String, Any>("id" to r.orderId)
                    )
                    if (rowsU > 0) {
                        log.info("RECONCILE: CONFIRMED order #{} ref={} (age={}m)", r.orderId, ref, ageMin)
                        runCatching { notifyEmail("notifyConfirmed", r.orderId) }
                            .onFailure { e -> log.debug("RECONCILE: notifyConfirmed ausente/falhou: {}", e.message) }
                    } else {
                        log.info("RECONCILE: order #{} já não está em SENT (provavelmente atualizado antes)", r.orderId)
                    }
                }
                "FAILED" -> {
                    val rowsU = jdbc.update(
                        """
                        UPDATE payment_payouts
                           SET status='FAILED', failed_at=NOW(), fail_reason=COALESCE(fail_reason,'Reconciled failure')
                         WHERE order_id=:id AND status='SENT'
                        """.trimIndent(),
                        mapOf<String, Any>("id" to r.orderId)
                    )
                    if (rowsU > 0) {
                        log.warn("RECONCILE: FAILED order #{} ref={} (age={}m)", r.orderId, ref, ageMin)
                        runCatching { notifyEmail("notifyFailed", r.orderId) }
                            .onFailure { e -> log.debug("RECONCILE: notifyFailed ausente/falhou: {}", e.message) }
                    } else {
                        log.info("RECONCILE: order #{} já não está em SENT (provavelmente atualizado antes)", r.orderId)
                    }
                }
                "NOT_FOUND" -> {
                    log.info("RECONCILE: ref={} ainda não encontrado no provedor (age={}m). Re-tentar depois.", ref, ageMin)
                }
                else -> {
                    log.warn("RECONCILE: status desconhecido '{}' para ref={} (order #{})", status, ref, r.orderId)
                }
            }
        }
    }

    private fun fetchSentOlderThan(minAgeSeconds: Long, limit: Int): List<Row> =
        jdbc.query(
            """
            SELECT order_id,
                   provider_ref,
                   sent_at,
                   to_char(amount_net, 'FM999990D00') AS amount_net,
                   CASE
                     WHEN length(pix_key) <= 6 THEN '***'
                     ELSE substr(pix_key,1,3) || '***' || substr(pix_key,length(pix_key)-2,3)
                   END AS pix_mask
              FROM payment_payouts
             WHERE status='SENT'
               AND sent_at <= NOW() - (:age || ' seconds')::interval
             ORDER BY sent_at ASC
             LIMIT :lim
            """.trimIndent(),
            mapOf<String, Any>(
                "age" to minAgeSeconds,
                "lim" to limit
            )
        ) { rs, _ ->
            Row(
                rs.getLong("order_id"),
                rs.getString("provider_ref"),
                rs.getObject("sent_at", OffsetDateTime::class.java),
                rs.getString("amount_net"),
                rs.getString("pix_mask")
            )
        }

    /**
     * ⚠️ Ajuste: NÃO bloqueie "P<id>" (idEnvio da Efí).
     * Apenas ignore refs em branco ou claramente de stub.
     */
    private fun isLocalLookingRef(ref: String): Boolean =
        ref.isBlank() || ref.startsWith("STUB-", ignoreCase = true)

    /**
     * Chama o provider via reflexão:
     * tenta localizar um bean que tenha o método `checkPayoutStatus(String): String`
     * retornando "CONFIRMED" | "FAILED" | "NOT_FOUND" | "UNKNOWN".
     */
    private fun checkStatusViaProvider(providerRef: String): String {
        val candidates = sequence {
            yieldAll(ctx.getBeanNamesForType(Any::class.java).asIterable())
        }.filter {
            it.contains("payout", ignoreCase = true) ||
                    it.contains("efi", ignoreCase = true) ||
                    it.contains("pix", ignoreCase = true)
        }.toList()

        for (name in candidates) {
            val bean = runCatching { ctx.getBean(name) }.getOrNull() ?: continue
            val m = bean.javaClass.methods.firstOrNull {
                it.name == "checkPayoutStatus" &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == String::class.java
            } ?: continue
            val ret = runCatching { m.invoke(bean, providerRef) as? String }.getOrNull()
            if (!ret.isNullOrBlank()) return ret
        }
        return "UNKNOWN"
    }

    /** Dispara e-mail via reflexão se existir método com a assinatura esperada. */
    private fun notifyEmail(method: String, orderId: Long) {
        // 1) Tenta pelo nome do bean mais provável
        val beanByName = runCatching { ctx.getBean("payoutEmailService") }.getOrNull()
        val bean = beanByName ?: runCatching {
            // 2) Tenta por reflexão pelo tipo, se existir
            val clazz = Class.forName("com.luizgasparetto.backend.monolito.services.PayoutEmailService")
            ctx.getBeansOfType(clazz).values.firstOrNull()
        }.getOrNull() ?: return

        val m = bean.javaClass.methods.firstOrNull {
            it.name == method && it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Long.TYPE
        } ?: bean.javaClass.methods.firstOrNull {
            it.name == method && it.parameterCount == 1 && it.parameterTypes[0] == Long::class.java
        } ?: return

        runCatching { m.invoke(bean, orderId) }.onFailure { e ->
            log.debug("RECONCILE: método {} em {} falhou: {}", method, bean.javaClass.simpleName, e.message)
        }
    }
}
