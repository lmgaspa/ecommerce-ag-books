// src/main/kotlin/com/luizgasparetto/backend/monolito/services/payout/pix/PayoutPixEmailService.kt
package com.luizgasparetto.backend.monolito.services.payout.pix

import com.luizgasparetto.backend.monolito.models.payout.PayoutEmail
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailStatus
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailType
import com.luizgasparetto.backend.monolito.repositories.PayoutEmailRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Year
import java.util.Locale

@Service
class PayoutPixEmailService(
    private val mailSender: JavaMailSender,
    private val orderRepository: com.luizgasparetto.backend.monolito.repositories.OrderRepository,
    private val payoutEmailRepository: PayoutEmailRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    @Value("\${email.author}") private val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") private val brandName: String,
    @Value("\${mail.from:}") private val configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br.jpg}") private val logoUrl: String,
    @Value("\${application.timezone:America/Bahia}") private val appTz: String,
    // >>> CPF/Chave Pix do favorecido (global). Pode ser vazio; pode ser sobrescrito por parâmetro nos métodos.
    @Value("\${efi.payout.favored-key:}") private val favoredKeyFromConfig: String
) {
    private val log = LoggerFactory.getLogger(PayoutPixEmailService::class.java)
    private val fmtDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale("pt","BR"))

    // ---------- público: sucesso ----------
    fun sendPayoutConfirmedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,          // se nulo, usa favoredKeyFromConfig
        idEnvio: String,
        endToEndId: String? = null,
        txid: String? = null,
        efetivadoEm: OffsetDateTime? = OffsetDateTime.now(ZoneId.of(appTz)),
        extraNote: String? = null,
        to: String = authorEmail               // pode sobrescrever para multi-autor
    ) {
        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "✅ Repasse PIX confirmado (#$orderId) — $brandName"
        val html = buildHtml(
            success = true,
            orderId = orderId,
            amount = amount,
            payeePixKey = key,
            idEnvio = idEnvio,
            endToEndId = endToEndId,
            txid = txid,
            whenStr = efetivadoEm?.atZoneSameInstant(ZoneId.of(appTz))?.toLocalDateTime()?.format(fmtDateTime) ?: "",
            errorCode = null,
            errorMsg = null,
            note = extraNote
        )
        val success = send(to, subject, html, orderId, PayoutEmailType.REPASSE_PIX)
        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_PIX,
            status = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED,
            errorMessage = if (!success) "Erro ao enviar e-mail (exceção capturada)" else null
        )
    }

    // ---------- público: falha ----------
    fun sendPayoutFailedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,          // se nulo, usa favoredKeyFromConfig
        idEnvio: String,
        errorCode: String,
        errorMsg: String,
        to: String = authorEmail,
        txid: String? = null,
        endToEndId: String? = null,
        triedAt: OffsetDateTime? = OffsetDateTime.now(ZoneId.of(appTz)),
        extraNote: String? = null
    ) {
        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "❌ Repasse PIX não realizado (#$orderId) — $brandName"
        val html = buildHtml(
            success = false,
            orderId = orderId,
            amount = amount,
            payeePixKey = key,
            idEnvio = idEnvio,
            endToEndId = endToEndId,
            txid = txid,
            whenStr = triedAt?.atZoneSameInstant(ZoneId.of(appTz))?.toLocalDateTime()?.format(fmtDateTime) ?: "",
            errorCode = errorCode,
            errorMsg = errorMsg,
            note = extraNote
        )
        val success = send(to, subject, html, orderId, PayoutEmailType.REPASSE_PIX)
        // Para e-mails de falha, sempre registramos como FAILED se não conseguir enviar
        val finalStatus = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED
        val finalErrorMessage = if (!success) {
            "Erro ao enviar e-mail de falha: ${errorMsg}"
        } else {
            errorMsg  // Mantém a mensagem de erro original do repasse
        }
        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_PIX,
            status = finalStatus,
            errorMessage = finalErrorMessage
        )
    }

    // ---------- core mail ----------
    private fun send(to: String, subject: String, html: String, orderId: Long, emailType: PayoutEmailType): Boolean {
        val msg = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(msg, /* multipart = */ false, StandardCharsets.UTF_8.name())
        val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
        helper.setFrom(from, brandName)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)
        return try {
            mailSender.send(msg)
            log.info("MAIL Repasse PIX enviado -> {}", to)
            true
        } catch (e: Exception) {
            log.error("MAIL Repasse PIX ERRO para {}: {}", to, e.message, e)
            false
        }
    }

    // ---------- helper: busca payout_id a partir de order_id ----------
    private fun findPayoutIdByOrderId(orderId: Long): Long? {
        return try {
            val row = jdbc.queryForMap(
                "SELECT id FROM payment_payouts WHERE order_id = :orderId LIMIT 1",
                mapOf("orderId" to orderId)
            )
            (row["id"] as? Number)?.toLong()
        } catch (e: Exception) {
            log.warn("PayoutEmail: payout não encontrado para orderId={}: {}", orderId, e.message)
            null
        }
    }

    // ---------- persistência de e-mail ----------
    @Transactional
    private fun persistEmail(
        orderId: Long,
        to: String,
        emailType: PayoutEmailType,
        status: PayoutEmailStatus,
        errorMessage: String? = null
    ) {
        try {
            // Busca payout_id se existir (pode ser NULL para e-mails agendados)
            val payoutId = findPayoutIdByOrderId(orderId)

            // SEMPRE persiste o e-mail, mesmo sem payout_id (para segurança e auditoria)
            val payoutEmail = PayoutEmail(
                payoutId = payoutId,  // Pode ser NULL
                orderId = orderId,    // Sempre preenchido
                toEmail = to,
                emailType = emailType.name,
                sentAt = OffsetDateTime.now(),
                status = status,
                errorMessage = errorMessage
            )
            payoutEmailRepository.save(payoutEmail)
            
            if (payoutId != null) {
                log.debug("PayoutEmail: persistido orderId={} payoutId={} type={} status={}", orderId, payoutId, emailType, status)
            } else {
                log.debug("PayoutEmail: persistido orderId={} (sem payout_id ainda) type={} status={}", orderId, emailType, status)
            }
        } catch (e: Exception) {
            // Não quebra o fluxo se falhar ao persistir o log de e-mail
            log.error("PayoutEmail: erro ao persistir e-mail para orderId={}: {}", orderId, e.message, e)
        }
    }

    // ---------- HTML ----------
    private fun buildHtml(
        success: Boolean,
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String,
        idEnvio: String,
        endToEndId: String?,
        txid: String?,
        whenStr: String,
        errorCode: String?,
        errorMsg: String?,
        note: String?
    ): String {
        val valorFmt = "R$ %s".format(amount.setScale(2).toPlainString())
        val statusLine = if (success)
            "<p style=\"margin:0 0 6px\">🎉 <strong>Repasse PIX realizado com sucesso.</strong></p>"
        else
            "<p style=\"margin:0 0 6px\">❌ <strong>Repasse PIX não realizado.</strong></p>"

        val cpfFmt = formatCpfIfPossible(payeePixKey)
        val favorecidoLine = if (cpfFmt != null)
            "<p style=\"margin:6px 0\"><strong>👤 Favorecido (CPF):</strong> $cpfFmt</p>"
        else
            "<p style=\"margin:6px 0\"><strong>🎯 Favorecido (chave Pix):</strong> ${escape(payeePixKey)}</p>"

        val extraOk = if (success) buildString {
            txid?.takeIf { it.isNotBlank() }?.let { append("<p style='margin:4px 0'><strong>🔑 TXID:</strong> ${escape(it)}</p>") }
            endToEndId?.takeIf { it.isNotBlank() }?.let { append("<p style='margin:4px 0'><strong>🔗 EndToEndId:</strong> ${escape(it)}</p>") }
        } else ""

        val extraErr = if (!success) """
            <p style="margin:4px 0"><strong>Erro:</strong> ${escape(errorCode ?: "desconhecido")}</p>
            ${errorMsg?.let { "<p style='margin:4px 0;color:#a00'>${escape(it)}</p>" } ?: ""}
        """.trimIndent() else ""

        val noteBlock = note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação:</strong><br>${escape(it)}</p>"""
        } ?: ""

        // Buscar informações do cupom do pedido
        val couponBlock = try {
            val order = orderRepository.findById(orderId).orElse(null)
            if (order?.couponCode != null && order.discountAmount != null && order.discountAmount!! > java.math.BigDecimal.ZERO) {
                val couponCode = order.couponCode!!
                val discountAmount = order.discountAmount!!
                val discountFormatted = "R$ %.2f".format(discountAmount.toDouble())
                """
                <!-- CUPOM UTILIZADO NO PEDIDO -->
                <div style="background:#fff3cd;border:1px solid #ffeaa7;border-radius:8px;padding:16px;margin:16px 0;text-align:center;">
                  <div style="color:#856404;font-size:24px;margin-bottom:8px;">🎫</div>
                  <div style="font-weight:700;color:#856404;font-size:16px;margin-bottom:4px;">CUPOM UTILIZADO</div>
                  <div style="font-weight:600;color:#856404;font-size:14px;margin-bottom:8px;">Código: ${escape(couponCode)}</div>
                  <div style="font-weight:700;color:#856404;font-size:18px;">Pagamento reduzido em $discountFormatted</div>
                </div>
                """.trimIndent()
            } else ""
        } catch (e: Exception) {
            log.warn("Erro ao buscar informações do cupom para pedido $orderId: ${e.message}")
            ""
        }

        val subtitle = if (success) "Repasse PIX confirmado" else "Repasse PIX não realizado"

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
                    <div style="opacity:.9;font-size:12px;line-height:1.2;">$subtitle</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              $statusLine

              <p style="margin:6px 0"><strong>🧾 Pedido:</strong> #${escape(orderId.toString())}</p>
              <p style="margin:6px 0"><strong>💰 Valor repassado:</strong> $valorFmt</p>
              $favorecidoLine
              <p style="margin:6px 0"><strong>📦 Id do envio:</strong> ${escape(idEnvio)}</p>
              <p style="margin:6px 0"><strong>🕒 Data/hora:</strong> ${escape(whenStr)}</p>

              $extraOk
              $extraErr
              $couponBlock
              $noteBlock

              <p style="margin:16px 0 0;color:#555">
                Dúvidas? Fale com a <strong>${escape(brandName)}</strong><br>
                ✉️ <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> · 
                💬 <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
              </p>
            </div>

            <div style="background:linear-gradient(135deg,#0a2239,#0e4b68);color:#fff;
                        padding:6px 18px;text-align:center;font-size:14px;line-height:1;">
              <span role="img" aria-label="raio"
                    style="color:#ffd200;font-size:22px;vertical-align:middle;">&#x26A1;&#xFE0E;</span>
              <span style="vertical-align:middle;">© ${Year.now()} · Powered by
                <strong>AndesCoreSoftware</strong>
              </span>
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    // ---------- helpers (OCP-friendly) ----------
    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun onlyDigits(s: String): String = s.filter { it.isDigit() }

    private fun formatCpfIfPossible(key: String?): String? {
        val d = onlyDigits(key.orEmpty())
        return if (d.length == 11) "${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}-${d.substring(9)}" else null
    }
}
