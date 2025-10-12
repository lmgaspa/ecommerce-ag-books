package com.luizgasparetto.backend.monolito.payment.services

import com.luizgasparetto.backend.monolito.payment.efi.PaymentPixClient
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutEntity
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutRepository
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.random.Random

@Service
class PaymentPayoutOrchestrator(
    private val repo: PaymentPayoutRepository,
    private val pix: PaymentPixClient
) {

    private val log = LoggerFactory.getLogger(javaClass)
    @Transactional
    fun createAndSendIfAbsent(orderId: Long, authorId: Long, amount: BigDecimal, pixKey: String) {
        val existing = repo.findByOrderIdAndAuthorId(orderId, authorId)
        if (existing != null && existing.status in setOf(PaymentPayoutStatus.SENT, PaymentPayoutStatus.CONFIRMED)) return

        val payout = existing ?: repo.save(
            PaymentPayoutEntity(
                authorId = authorId,
                orderId = orderId,
                amount = amount.setScale(2, RoundingMode.HALF_UP),
                status = PaymentPayoutStatus.CREATED,
                pixKey = pixKey
            )
        )
        send(payout) // método que não relança exceção (marca FAILED + failReason)
    }

    @Transactional
    fun send(payout: PaymentPayoutEntity) {
        val idEnvio = genIdEnvio()
        try {
            pix.sendTransfer(idEnvio = idEnvio, pixKey = payout.pixKey, amount = payout.amount)
            payout.efiIdEnvio = idEnvio
            payout.status = PaymentPayoutStatus.SENT
            payout.sentAt = Instant.now()
            repo.save(payout)
        } catch (e: Exception) {
            payout.status = PaymentPayoutStatus.FAILED
            payout.failReason = e.message
            repo.save(payout)
            log.error("Payout FAILED id={}, reason={}", payout.id, e.message)
            // NADA de throw aqui
        }
    }

    private fun genIdEnvio(): String {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..26).map { alpha[Random.Default.nextInt(alpha.length)] }.joinToString("")
    }
}