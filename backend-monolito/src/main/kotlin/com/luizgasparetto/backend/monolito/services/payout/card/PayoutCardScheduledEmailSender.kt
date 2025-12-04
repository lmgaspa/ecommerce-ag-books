package com.luizgasparetto.backend.monolito.services.payout.card

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailStatus
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.PayoutEmailRepository
import com.luizgasparetto.backend.monolito.services.email.common.EmailFooter
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Responsável por enviar emails de repasse CARD agendado
 */
@Component
class PayoutCardScheduledEmailSender(
    mailSender: JavaMailSender,
    orderRepository: OrderRepository,
    payoutEmailRepository: PayoutEmailRepository,
    jdbc: NamedParameterJdbcTemplate,
    payoutProps: EfiPayoutProps,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String,
    @Value("\${application.timezone:America/Bahia}") appTz: String,
    @Value("\${efi.payout.favored-key:}") favoredKeyFromConfig: String,
    @Value("\${efi.card.sandbox:false}") sandbox: Boolean,
    @Value("\${mail.host:}") mailHost: String
) : PayoutCardEmailBase(
    mailSender, orderRepository, payoutEmailRepository, jdbc, payoutProps,
    authorEmail, brandName, configuredFrom, logoUrl, appTz, favoredKeyFromConfig, sandbox, mailHost
) {

    fun send(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,
        idEnvio: String,
        to: String = authorEmail,
        extraNote: String? = null
    ) {
        val from = resolveFrom()

        log.info(
            "📧 PAYOUT CARD EMAIL [SCHEDULED]: iniciando envio - orderId={}, to={}, sandbox={}, mailHost={}",
            orderId, to, sandbox, mailHost
        )
        log.debug(
            "📧 PAYOUT CARD EMAIL [SCHEDULED]: config -> from={}, authorEmail={}, appTz={}, favoredKeyFromConfig={}, logoUrl={}",
            from, authorEmail, appTz, favoredKeyFromConfig, logoUrl
        )

        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "📅 Repasse de Cartão programado (#$orderId) — $brandName"
        val html = buildScheduledHtml(
            orderId = orderId,
            amount = amount,
            payeePixKey = key,
            idEnvio = idEnvio,
            note = extraNote
        )

        val success = sendInternal(
            to = to,
            subject = subject,
            html = html,
            orderId = orderId,
            context = "SCHEDULED"
        )

        log.debug(
            "📧 PAYOUT CARD EMAIL [SCHEDULED]: resultado do envio - orderId={}, success={}, sandbox={}",
            orderId, success, sandbox
        )

        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_CARD,
            status = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED,
            errorMessage = if (!success) "Erro ao enviar e-mail agendado (exceção capturada)" else null
        )
    }

    private fun buildScheduledHtml(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String,
        idEnvio: String,
        note: String?
    ): String {
        val amountNet = calculateNetAmount(amount)
        val valorFmt = "R$ %s".format(amountNet.setScale(2).toPlainString())
        val cpfFmt = formatCpfIfPossible(payeePixKey)
        val favorecidoLine = if (cpfFmt != null)
            "<p style=\"margin:6px 0\"><strong>👤 Favorecido (CPF):</strong> $cpfFmt</p>"
        else
            "<p style=\"margin:6px 0\"><strong>🎯 Favorecido (chave Pix):</strong> ${escape(payeePixKey)}</p>"

        val noteBlock = note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação:</strong><br>${escape(it)}</p>"""
        } ?: ""

        val couponBlock = buildCouponBlock(orderId)

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:12px;overflow:hidden">

            <!-- HEADER -->
            <div style="background:linear-gradient(135deg,#0a2239,#0e4b68);color:#fff;padding:16px 20px;">
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                <tr>
                  <td style="width:64px;vertical-align:middle;">
                    <img src="$logoUrl" alt="${escape(brandName)}" width="56" style="display:block;border-radius:6px;">
                  </td>
                  <td style="text-align:right;vertical-align:middle;">
                    <div style="font-weight:700;font-size:18px;line-height:1;">${escape(brandName)}</div>
                    <div style="height:6px;line-height:6px;font-size:0;">&nbsp;</div>
                    <div style="opacity:.9;font-size:12px;line-height:1.2;">Repasse de Cartão programado</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              <p style="margin:0 0 6px">📅 <strong>Repasse de Cartão programado para 32 dias.</strong></p>
              <p style="margin:6px 0;color:#666;font-size:14px;">Por política da Efí Bank, repasses de cartão são processados após 32 dias da aprovação.</p>

              <p style="margin:6px 0"><strong>🧾 Pedido:</strong> #${escape(orderId.toString())}</p>
              <p style="margin:6px 0"><strong>💰 Valor a ser repassado:</strong> $valorFmt</p>
              $favorecidoLine
              <p style="margin:6px 0"><strong>📦 Id do envio:</strong> ${escape(idEnvio)}</p>
              <p style="margin:6px 0"><strong>📅 Data prevista:</strong> 32 dias após aprovação</p>

              <div style="background:#f0f8ff;border-left:4px solid #2196f3;padding:12px;margin:16px 0;">
                <p style="margin:0;font-size:14px;color:#1976d2;">
                  <strong>ℹ️ Informação importante:</strong><br>
                  O repasse será processado automaticamente em 32 dias, conforme política da Efí Bank. 
                  Você receberá um novo email quando o repasse for efetivado.
                </p>
              </div>

              $couponBlock
              $noteBlock

              <p style="margin:16px 0 0;color:#555">
                Dúvidas? Fale com a <strong>${escape(brandName)}</strong><br>
                ✉️ <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> ·
                💬 <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
              </p>
            </div>

            ${EmailFooter.build()}
          </div>
        </body>
        </html>
        """.trimIndent()
    }
}

