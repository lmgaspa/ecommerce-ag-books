package com.luizgasparetto.backend.monolito.services.payout.pix

import com.luizgasparetto.backend.monolito.config.payments.EfiPixPayoutProps
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmail
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailStatus
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.PayoutEmailRepository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/** Responsável por enviar emails de falha de repasse PIX */
@Component
class PayoutPixFailedEmailSender(
        mailSender: JavaMailSender,
        orderRepository: OrderRepository,
        jdbc: NamedParameterJdbcTemplate,
        payoutProps: EfiPixPayoutProps,
        private val payoutEmailRepository: PayoutEmailRepository,
        @Value("\${email.author}") authorEmail: String,
        @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
        @Value("\${mail.from:}") configuredFrom: String,
        @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}")
        logoUrl: String,
        @Value("\${application.timezone:America/Bahia}") appTz: String,
        @Value("\${efi.payout.favored-key:}") favoredKeyFromConfig: String,
        @Value("\${efi.pix.sandbox:false}") sandbox: Boolean,
        @Value("\${mail.host:}") mailHost: String
) :
        PayoutPixEmailBase(
                mailSender,
                orderRepository,
                jdbc,
                payoutProps,
                authorEmail,
                brandName,
                configuredFrom,
                logoUrl,
                appTz,
                favoredKeyFromConfig,
                sandbox,
                mailHost
        ) {
    private val fmtDateTime: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    fun send(
            orderId: Long,
            amount: BigDecimal,
            payeePixKey: String? = null,
            idEnvio: String,
            errorCode: String,
            errorMsg: String,
            to: String = authorEmail,
            txid: String? = null,
            endToEndId: String? = null,
            triedAt: OffsetDateTime? = OffsetDateTime.now(ZoneId.of(appTz)),
            extraNote: String? = null
    ) {
        log.info(
                "📧 PAYOUT PIX EMAIL [FAILED]: iniciando envio - orderId={}, to={}, sandbox={}, mailHost={}, errorCode={}",
                orderId,
                to,
                sandbox,
                mailHost,
                errorCode
        )

        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "❌ Repasse PIX não realizado (#$orderId) — $brandName"
        val whenStr =
                triedAt?.atZoneSameInstant(ZoneId.of(appTz))?.toLocalDateTime()?.format(fmtDateTime)
                        ?: ""

        val html =
                buildHtml(
                        orderId = orderId,
                        amount = amount,
                        payeePixKey = key,
                        idEnvio = idEnvio,
                        endToEndId = endToEndId,
                        txid = txid,
                        whenStr = whenStr,
                        errorCode = errorCode,
                        errorMsg = errorMsg,
                        note = extraNote
                )

        val success =
                sendInternal(
                        to = to,
                        subject = subject,
                        html = html,
                        orderId = orderId,
                        context = "FAILED"
                )

        val finalStatus = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED
        val finalErrorMessage =
                if (!success) {
                    "Erro ao enviar e-mail de falha: $errorMsg"
                } else {
                    errorMsg // mantém a mensagem original do repasse
                }

        persistEmail(
                orderId = orderId,
                to = to,
                status = finalStatus,
                errorMessage = finalErrorMessage
        )
    }

    private fun buildHtml(
            orderId: Long,
            amount: BigDecimal,
            payeePixKey: String,
            idEnvio: String,
            endToEndId: String?,
            txid: String?,
            whenStr: String,
            errorCode: String,
            errorMsg: String,
            note: String?
    ): String {
        val amountNet = calculateNetAmount(amount)
        val valorFmt = "R$ %s".format(amountNet.setScale(2).toPlainString())

        val cpfFmt = formatCpfIfPossible(payeePixKey)
        val favorecidoLine =
                if (cpfFmt != null) {
                    "<p style=\"margin:6px 0\"><strong>👤 Favorecido (CPF):</strong> $cpfFmt</p>"
                } else {
                    "<p style=\"margin:6px 0\"><strong>🎯 Favorecido (chave Pix):</strong> ${escape(payeePixKey)}</p>"
                }

        val extraErr =
                """
            <p style="margin:4px 0"><strong>Erro:</strong> ${escape(errorCode)}</p>
            ${
                errorMsg.takeIf { it.isNotBlank() }?.let {
                    "<p style='margin:4px 0;color:#a00'>${escape(it)}</p>"
                } ?: ""
            }
        """.trimIndent()

        val noteBlock =
                note?.takeIf { it.isNotBlank() }?.let {
                    """<p style="margin:10px 0 0"><strong>📝 Observação:</strong><br>${escape(it)}</p>"""
                }
                        ?: ""

        val couponBlock = buildCouponBlock(orderId)

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid:#eee;border-radius:12px;overflow:hidden">

            ${buildHeader("Repasse PIX não realizado")}

            <div style="padding:20px">
              <p style="margin:0 0 6px">❌ <strong>Repasse PIX não realizado.</strong></p>

              <p style="margin:6px 0"><strong>🧾 Pedido:</strong> #${escape(orderId.toString())}</p>
              <p style="margin:6px 0"><strong>💰 Valor repassado:</strong> $valorFmt</p>
              $favorecidoLine
              <p style="margin:6px 0"><strong>📦 Id do envio:</strong> ${escape(idEnvio)}</p>
              <p style="margin:6px 0"><strong>🕒 Data/hora:</strong> ${escape(whenStr)}</p>

              $extraErr
              $couponBlock
              $noteBlock

              ${buildContactBlock()}
            </div>

            ${buildFooter()}
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun persistEmail(
            orderId: Long,
            to: String,
            status: PayoutEmailStatus,
            errorMessage: String?
    ) {
        try {
            val payoutId = findPayoutIdByOrderId(orderId)

            val payoutEmail =
                    PayoutEmail(
                            payoutId = payoutId,
                            orderId = orderId,
                            toEmail = to,
                            emailType = PayoutEmailType.REPASSE_PIX.name,
                            sentAt = OffsetDateTime.now(),
                            status = status,
                            errorMessage = errorMessage
                    )

            payoutEmailRepository.save(payoutEmail)

            if (payoutId != null) {
                log.debug(
                        "PayoutEmail [PIX]: persistido (com payoutId) orderId={} payoutId={} type={} status={} error={}",
                        orderId,
                        payoutId,
                        PayoutEmailType.REPASSE_PIX,
                        status,
                        errorMessage
                )
            } else {
                log.debug(
                        "PayoutEmail [PIX]: persistido (sem payoutId) orderId={} type={} status={} error={}",
                        orderId,
                        PayoutEmailType.REPASSE_PIX,
                        status,
                        errorMessage
                )
            }
        } catch (e: Exception) {
            log.error(
                    "PayoutEmail [PIX]: erro ao persistir e-mail para orderId={}: {}",
                    orderId,
                    e.message,
                    e
            )
        }
    }
}
