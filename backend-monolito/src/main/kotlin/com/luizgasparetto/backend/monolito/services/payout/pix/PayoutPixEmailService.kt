package com.luizgasparetto.backend.monolito.services.payout.pix

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Facade para envio de emails de Payout PIX.
 * Delega para classes específicas: PayoutPixConfirmedEmailSender e PayoutPixFailedEmailSender
 */
@Service
class PayoutPixEmailService(
    private val confirmedSender: PayoutPixConfirmedEmailSender,
    private val failedSender: PayoutPixFailedEmailSender
) {

    /**
     * Envia email de confirmação de repasse PIX
     */
    fun sendPayoutConfirmedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,
        idEnvio: String,
        endToEndId: String? = null,
        txid: String? = null,
        efetivadoEm: OffsetDateTime? = null,
        extraNote: String? = null,
        to: String? = null
    ) {
        if (to != null) {
            confirmedSender.send(
                orderId = orderId,
                amount = amount,
                payeePixKey = payeePixKey,
                idEnvio = idEnvio,
                endToEndId = endToEndId,
                txid = txid,
                efetivadoEm = efetivadoEm,
                extraNote = extraNote,
                to = to
            )
        } else {
            confirmedSender.send(
                orderId = orderId,
                amount = amount,
                payeePixKey = payeePixKey,
                idEnvio = idEnvio,
                endToEndId = endToEndId,
                txid = txid,
                efetivadoEm = efetivadoEm,
                extraNote = extraNote
            )
        }
    }

    /**
     * Envia email de falha de repasse PIX
     */
    fun sendPayoutFailedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,
        idEnvio: String,
        errorCode: String,
        errorMsg: String,
        to: String? = null,
        txid: String? = null,
        endToEndId: String? = null,
        triedAt: OffsetDateTime? = null,
        extraNote: String? = null
    ) {
        if (to != null) {
            failedSender.send(
                orderId = orderId,
                amount = amount,
                payeePixKey = payeePixKey,
                idEnvio = idEnvio,
                errorCode = errorCode,
                errorMsg = errorMsg,
                to = to,
                txid = txid,
                endToEndId = endToEndId,
                triedAt = triedAt,
                extraNote = extraNote
            )
        } else {
            failedSender.send(
                orderId = orderId,
                amount = amount,
                payeePixKey = payeePixKey,
                idEnvio = idEnvio,
                errorCode = errorCode,
                errorMsg = errorMsg,
                txid = txid,
                endToEndId = endToEndId,
                triedAt = triedAt,
                extraNote = extraNote
            )
        }
    }
}
