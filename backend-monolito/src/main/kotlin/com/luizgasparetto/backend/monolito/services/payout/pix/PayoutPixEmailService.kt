// src/main/kotlin/com/luizgasparetto/backend/monolito/services/payout/pix/PayoutPixEmailService.kt
package com.luizgasparetto.backend.monolito.services.payout.pix

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
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
        send(to, subject, html)
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
        send(to, subject, html)
    }

    // ---------- core mail ----------
    private fun send(to: String, subject: String, html: String) {
        val msg = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(msg, /* multipart = */ false, StandardCharsets.UTF_8.name())
        val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
        helper.setFrom(from, brandName)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)
        try {
            mailSender.send(msg)
            log.info("MAIL Repasse PIX enviado -> {}", to)
        } catch (e: Exception) {
            log.error("MAIL Repasse PIX ERRO para {}: {}", to, e.message, e)
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
