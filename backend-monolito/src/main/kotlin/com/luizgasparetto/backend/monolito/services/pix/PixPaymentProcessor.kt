package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.email.PixEmailService
import com.luizgasparetto.backend.monolito.services.order.OrderEventsPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import com.luizgasparetto.backend.monolito.payments.web.PaymentTriggerService
import com.luizgasparetto.backend.monolito.services.payout.pix.PayoutPixEmailService
import java.math.BigDecimal

@Service
class PixPaymentProcessor(
    private val orderRepository: OrderRepository,
    private val emailService: PixEmailService,
    private val events: OrderEventsPublisher,
    private val payoutTrigger: PaymentTriggerService,
    private val payoutEmail: PayoutPixEmailService // <- e-mails de repasse (confirmado/falha)
) {
    private val log = LoggerFactory.getLogger(PixPaymentProcessor::class.java)
    private val paidStatuses = setOf("CONCLUIDA","LIQUIDADO","LIQUIDADA","ATIVA-RECEBIDA","COMPLETED","PAID")

    fun isPaidStatus(status: String?): Boolean =
        status != null && paidStatuses.contains(status.uppercase())

    @Transactional
    fun markPaidIfNeededByTxid(txid: String): Boolean {
        val order = orderRepository.findWithItemsByTxid(txid) ?: return false
        if (order.paid) return true

        val now = OffsetDateTime.now()

        // Só confirma se estiver WAITING e dentro do TTL
        if (order.status != OrderStatus.WAITING) {
            log.info("POLL: ignorado txid={}, status atual={}", txid, order.status)
            return false
        }
        if (order.reserveExpiresAt != null && now.isAfter(order.reserveExpiresAt)) {
            log.info("POLL: ignorado txid={}, pagamento após TTL", txid)
            return false
        }

        order.paid = true
        order.paidAt = now
        order.status = OrderStatus.CONFIRMED
        orderRepository.save(order)
        log.info("POLL: order {} CONFIRMED (txid={})", order.id, txid)

        // Pós-pagamento (e-mails + SSE)
        runCatching {
            emailService.sendPixClientEmail(order)
            emailService.sendPixAuthorEmail(order)
            order.id?.let { events.publishPaid(it) }
        }.onFailure { e ->
            log.warn("POLL: pós-pagamento com falha: {}", e.message)
        }

        // 🔔 Dispara o orquestrador de repasse (idempotente)
        runCatching {
            val result = payoutTrigger.tryTriggerByRef(
                orderRef = order.id?.toString(),
                externalId = txid,
                sourceProvider = "PIX-POLLER"
            )

            when (result.status) {
                "SUCCESS" -> {
                    log.info(
                        "PAYOUT: SENT order #{} ref={} gross={} net={} min={} key={}",
                        result.orderId, result.providerRef, result.amountGross, result.amountNet,
                        result.minSend, result.pixKey?.let { mask(it) }
                    )

                    // Envia email de repasse confirmado imediatamente para PIX
                    // (PIX é instantâneo, não precisa aguardar conciliação)
                    if (result.orderId != null) {
                        safeSendConfirmedEmail(
                            orderId = result.orderId,
                            amount = result.amountNet ?: BigDecimal.ZERO,
                            payeePixKey = result.pixKey,
                            idEnvio = result.providerRef ?: "P${result.orderId}",
                            note = "Repasse PIX confirmado automaticamente."
                        )
                    }
                }
                "FAILED", "ERROR" -> {
                    log.warn(
                        "PAYOUT: {} order #{} msg={} gross={} net={} min={} key={}",
                        result.status, result.orderId, result.message, result.amountGross, result.amountNet,
                        result.minSend, result.pixKey?.let { mask(it) }
                    )

                    // Dispara e-mail de falha de repasse ainda no poller (útil se falhar antes de enviar ao provider)
                    if (result.orderId != null) {
                        safeSendFailedEmail(
                            orderId = result.orderId,
                            amount = result.amountNet ?: BigDecimal.ZERO,
                            payeePixKey = result.pixKey,
                            idEnvio = result.providerRef ?: "P${result.orderId}",
                            errorCode = result.status, // FAILED ou ERROR
                            errorMsg = result.message ?: "Falha ao acionar repasse (poller).",
                            note = "Falha na etapa de disparo do repasse (poller)."
                        )
                    }
                }
            }
        }.onFailure { e ->
            log.error("PAYOUT trigger (poller) falhou. txid={}, orderId={}, err={}", txid, order.id, e.message, e)
            // Em caso de exceção dura, também avisamos por e-mail (se possível)
            order.id?.let { oid ->
                safeSendFailedEmail(
                    orderId = oid,
                    amount = BigDecimal.ZERO, // desconhecido aqui
                    payeePixKey = null,
                    idEnvio = "P$oid",
                    errorCode = "EXCEPTION",
                    errorMsg = e.message ?: "Exceção ao acionar repasse no poller.",
                    note = "Handler de exceção do poller."
                )
            }
        }

        return true
    }

    private fun mask(k: String) = if (k.length <= 6) "***" else k.take(3) + "***" + k.takeLast(3)

    // ------- helpers de e-mail de repasse -------

    private fun safeSendConfirmedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        note: String
    ) {
        runCatching {
            payoutEmail.sendPayoutConfirmedEmail(
                orderId = orderId,
                amount = amount,
                payeePixKey = payeePixKey,
                idEnvio = idEnvio,
                endToEndId = null,
                txid = null,
                extraNote = note
            )
        }.onFailure { e ->
            log.warn("MAIL payout CONFIRMED (poller) falhou order #{}: {}", orderId, e.message)
        }
    }

    private fun safeSendFailedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        errorCode: String,
        errorMsg: String,
        note: String
    ) {
        runCatching {
            payoutEmail.sendPayoutFailedEmail(
                orderId = orderId,
                amount = amount,
                payeePixKey = payeePixKey,
                idEnvio = idEnvio,
                errorCode = errorCode,
                errorMsg = errorMsg,
                txid = null,
                endToEndId = null,
                extraNote = note
            )
        }.onFailure { e ->
            log.warn("MAIL payout FAILED (poller) falhou order #{}: {}", orderId, e.message)
        }
    }
}
