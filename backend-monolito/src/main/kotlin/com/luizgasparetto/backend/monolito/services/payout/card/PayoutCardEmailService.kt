package com.luizgasparetto.backend.monolito.services.payout.card

import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Facade para envio de emails de Payout CARD.
 * Delega para classes específicas: PayoutCardConfirmedEmailSender, PayoutCardScheduledEmailSender e PayoutCardFailedEmailSender
 */
@Service
class PayoutCardEmailService(
    private val confirmedSender: PayoutCardConfirmedEmailSender,
    private val scheduledSender: PayoutCardScheduledEmailSender,
    private val failedSender: PayoutCardFailedEmailSender
) {

    /**
     * Envia email de confirmação de repasse CARD
     */
    fun sendPayoutConfirmedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        note: String? = null
    ) {
        confirmedSender.send(
            orderId = orderId,
            amount = amount,
            payeePixKey = payeePixKey,
            idEnvio = idEnvio,
            note = note
        )
    }

    /**
     * Envia email de repasse CARD agendado
     */
    fun sendPayoutScheduledEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,
        idEnvio: String,
        to: String = scheduledSender.authorEmail,
        extraNote: String? = null
    ) {
        scheduledSender.send(
            orderId = orderId,
            amount = amount,
            payeePixKey = payeePixKey,
            idEnvio = idEnvio,
            to = to,
            extraNote = extraNote
        )
    }

    /**
     * Envia email de falha de repasse CARD
     */
    fun sendPayoutFailedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        errorCode: String,
        errorMessage: String
    ) {
        failedSender.send(
            orderId = orderId,
            amount = amount,
            payeePixKey = payeePixKey,
            idEnvio = idEnvio,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }
}
