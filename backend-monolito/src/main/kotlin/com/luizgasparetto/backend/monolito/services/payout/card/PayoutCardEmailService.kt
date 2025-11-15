package com.luizgasparetto.backend.monolito.services.payout.card

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
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Year
import java.util.Locale
import java.nio.charset.StandardCharsets

@Service
class PayoutCardEmailService(
    private val mailSender: JavaMailSender,
    private val orderRepository: com.luizgasparetto.backend.monolito.repositories.OrderRepository,
    private val payoutEmailRepository: PayoutEmailRepository,
    private val jdbc: NamedParameterJdbcTemplate,
    @Value("\${email.author}") private val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") private val brandName: String,
    @Value("\${mail.from:}") private val configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br.jpg}") private val logoUrl: String,
    @Value("\${application.timezone:America/Bahia}") private val appTz: String,
    @Value("\${efi.payout.favored-key:}") private val favoredKeyFromConfig: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val fmtDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale("pt","BR"))

    fun sendPayoutConfirmedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        note: String? = null
    ) {
        val to = authorEmail
        val success = try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
            helper.setFrom(from, brandName)
            helper.setTo(to)
            helper.setSubject("💰 Repasse Cartão Confirmado - Pedido #$orderId")

            val maskedKey = payeePixKey?.let { mask(it) } ?: "N/A"
            val amountFormatted = String.format("R$ %.2f", amount)

            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: #28a745; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                        .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 8px 8px; }
                        .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #28a745; }
                        .amount { font-size: 24px; font-weight: bold; color: #28a745; }
                        .footer { text-align: center; margin-top: 30px; color: #666; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>💰 Repasse Cartão Confirmado</h1>
                            <p>Pagamento processado com sucesso</p>
                        </div>
                        <div class="content">
                            <div class="info-box">
                                <h3>📋 Detalhes do Repasse</h3>
                                <p><strong>Pedido:</strong> #$orderId</p>
                                <p><strong>Valor:</strong> <span class="amount">$amountFormatted</span></p>
                                <p><strong>Chave PIX:</strong> $maskedKey</p>
                                <p><strong>ID Envio:</strong> $idEnvio</p>
                                ${note?.let { "<p><strong>Observação:</strong> $it</p>" } ?: ""}
                            </div>
                            
                            <div class="info-box">
                                <h3>✅ Status</h3>
                                <p>O repasse foi processado com sucesso e o valor foi transferido para a conta do autor.</p>
                            </div>
                        </div>
                        <div class="footer">
                            <p>Este é um email automático do sistema de e-commerce.</p>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            helper.setText(htmlContent, true)
            mailSender.send(message)

            log.info("PAYOUT CARD EMAIL: enviado com sucesso -> {} (order #{}, amount={}, key={})", 
                to, orderId, amountFormatted, maskedKey)
            true
        } catch (e: Exception) {
            log.error("PAYOUT CARD EMAIL: falha ao enviar (order #{}, amount={}): {}", orderId, amount, e.message)
            false
        }

        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_CARD,
            status = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED,
            errorMessage = if (!success) "Erro ao enviar e-mail (exceção capturada)" else null
        )
    }

    fun sendPayoutScheduledEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,
        idEnvio: String,
        to: String = authorEmail,
        extraNote: String? = null
    ) {
        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "📅 Repasse de Cartão programado (#$orderId) — $brandName"
        val html = buildScheduledHtml(
            orderId = orderId,
            amount = amount,
            payeePixKey = key,
            idEnvio = idEnvio,
            note = extraNote
        )
        val success = send(to, subject, html, orderId)
        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_CARD,
            status = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED,
            errorMessage = if (!success) "Erro ao enviar e-mail agendado (exceção capturada)" else null
        )
    }

    fun sendPayoutFailedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        errorCode: String,
        errorMessage: String
    ) {
        val to = authorEmail
        val success = try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
            helper.setFrom(from, brandName)
            helper.setTo(to)
            helper.setSubject("❌ Falha no Repasse Cartão - Pedido #$orderId")

            val maskedKey = payeePixKey?.let { mask(it) } ?: "N/A"
            val amountFormatted = String.format("R$ %.2f", amount)

            val htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: #dc3545; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                        .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 8px 8px; }
                        .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #dc3545; }
                        .amount { font-size: 24px; font-weight: bold; color: #dc3545; }
                        .error { background: #f8d7da; color: #721c24; padding: 15px; border-radius: 4px; margin: 10px 0; }
                        .footer { text-align: center; margin-top: 30px; color: #666; font-size: 14px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>❌ Falha no Repasse Cartão</h1>
                            <p>Não foi possível processar o repasse</p>
                        </div>
                        <div class="content">
                            <div class="info-box">
                                <h3>📋 Detalhes do Repasse</h3>
                                <p><strong>Pedido:</strong> #$orderId</p>
                                <p><strong>Valor:</strong> <span class="amount">$amountFormatted</span></p>
                                <p><strong>Chave PIX:</strong> $maskedKey</p>
                                <p><strong>ID Envio:</strong> $idEnvio</p>
                            </div>
                            
                            <div class="info-box">
                                <h3>❌ Erro</h3>
                                <div class="error">
                                    <p><strong>Código:</strong> $errorCode</p>
                                    <p><strong>Mensagem:</strong> $errorMessage</p>
                                </div>
                                <p>Por favor, verifique os dados e tente novamente ou entre em contato com o suporte.</p>
                            </div>
                        </div>
                        <div class="footer">
                            <p>Este é um email automático do sistema de e-commerce.</p>
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            helper.setText(htmlContent, true)
            mailSender.send(message)

            log.info("PAYOUT CARD EMAIL: falha enviada -> {} (order #{}, amount={}, error={})", 
                to, orderId, amountFormatted, errorCode)
            true
        } catch (e: Exception) {
            log.error("PAYOUT CARD EMAIL: falha ao enviar email de erro (order #{}, amount={}): {}", orderId, amount, e.message)
            false
        }

        val finalStatus = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED
        val finalErrorMessage = if (!success) {
            "Erro ao enviar e-mail de falha: $errorMessage"
        } else {
            errorMessage
        }
        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_CARD,
            status = finalStatus,
            errorMessage = finalErrorMessage
        )
    }

    private fun buildScheduledHtml(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String,
        idEnvio: String,
        note: String?
    ): String {
        val valorFmt = "R$ %s".format(amount.setScale(2).toPlainString())
        val cpfFmt = formatCpfIfPossible(payeePixKey)
        val favorecidoLine = if (cpfFmt != null)
            "<p style=\"margin:6px 0\"><strong>👤 Favorecido (CPF):</strong> $cpfFmt</p>"
        else
            "<p style=\"margin:6px 0\"><strong>🎯 Favorecido (chave Pix):</strong> ${escape(payeePixKey)}</p>"

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
              <p style="margin:0 0 6px">📅 <strong>Repasse de Cartão programado para 31 dias.</strong></p>
              <p style="margin:6px 0;color:#666;font-size:14px;">Por política da Efí Bank, repasses de cartão são processados após 31 dias da aprovação.</p>

              <p style="margin:6px 0"><strong>🧾 Pedido:</strong> #${escape(orderId.toString())}</p>
              <p style="margin:6px 0"><strong>💰 Valor a ser repassado:</strong> $valorFmt</p>
              $favorecidoLine
              <p style="margin:6px 0"><strong>📦 Id do envio:</strong> ${escape(idEnvio)}</p>
              <p style="margin:6px 0"><strong>📅 Data prevista:</strong> 31 dias após aprovação</p>

              <div style="background:#f0f8ff;border-left:4px solid #2196f3;padding:12px;margin:16px 0;">
                <p style="margin:0;font-size:14px;color:#1976d2;">
                  <strong>ℹ️ Informação importante:</strong><br>
                  O repasse será processado automaticamente em 31 dias, conforme política da Efí Bank. 
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

    private fun send(to: String, subject: String, html: String, orderId: Long): Boolean {
        val msg = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(msg, /* multipart = */ false, StandardCharsets.UTF_8.name())
        val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
        helper.setFrom(from, brandName)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)
        return try {
            mailSender.send(msg)
            log.info("MAIL Repasse de Cartão enviado -> {}", to)
            true
        } catch (e: Exception) {
            log.error("MAIL Repasse de Cartão ERRO para {}: {}", to, e.message, e)
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

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun onlyDigits(s: String): String = s.filter { it.isDigit() }

    private fun formatCpfIfPossible(key: String?): String? {
        val d = onlyDigits(key.orEmpty())
        return if (d.length == 11) "${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}-${d.substring(9)}" else null
    }

    private fun mask(pixKey: String): String {
        return if (pixKey.length <= 6) {
            "***"
        } else {
            pixKey.take(3) + "***" + pixKey.takeLast(3)
        }
    }
}
