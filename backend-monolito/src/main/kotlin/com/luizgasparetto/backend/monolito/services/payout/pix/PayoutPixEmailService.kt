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
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.Year
import java.time.format.DateTimeFormatter
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
    @Value("\${efi.payout.favored-key:}") private val favoredKeyFromConfig: String,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean,
    @Value("\${mail.host:}") private val mailHost: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val fmtDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))

    // ----------------------------------------------------------------
    // IDEMPOTÊNCIA: checa se já existe e-mail de REPASSE_PIX SENT
    // ----------------------------------------------------------------

    private fun alreadySentConfirmedRepasse(orderId: Long): Boolean {
        val payoutId = findPayoutIdByOrderId(orderId)

        if (payoutId == null) {
            log.warn(
                "PayoutEmail [PIX]: não foi possível encontrar payoutId para orderId={}, não será feita checagem de idempotência.",
                orderId
            )
            return false
        }

        val exists = payoutEmailRepository.existsByPayoutIdAndEmailTypeAndStatus(
            payoutId,
            PayoutEmailType.REPASSE_PIX.name,   // String, casa com emailType
            PayoutEmailStatus.SENT
        )

        if (exists) {
            log.info(
                "PayoutEmail [PIX]: e-mail REPASSE_PIX já enviado (payoutId={}, orderId={}), pulando novo envio.",
                payoutId, orderId
            )
        }

        return exists
    }

    // ----------------------------------------------------------------
    // PÚBLICO: SUCESSO
    // ----------------------------------------------------------------

    fun sendPayoutConfirmedEmail(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String? = null,          // se nulo, usa favoredKeyFromConfig
        idEnvio: String,
        endToEndId: String? = null,
        txid: String? = null,
        efetivadoEm: OffsetDateTime? = OffsetDateTime.now(ZoneId.of(appTz)),
        extraNote: String? = null,
        to: String = authorEmail              // pode sobrescrever para multi-autor
    ) {
        log.info(
            "📧 PAYOUT PIX EMAIL [CONFIRMED]: iniciando envio - orderId={}, to={}, sandbox={}, mailHost={}",
            orderId, to, sandbox, mailHost
        )

        // 🔒 Idempotência: se já existe e-mail SENT de REPASSE_PIX para este payout, não envia de novo
        if (alreadySentConfirmedRepasse(orderId)) {
            return
        }

        val from = resolveFrom()

        log.debug(
            "📧 PAYOUT PIX EMAIL [CONFIRMED]: config -> from={}, authorEmail={}, appTz={}, favoredKeyFromConfig={}, logoUrl={}",
            from, authorEmail, appTz, favoredKeyFromConfig, logoUrl
        )

        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "✅ Repasse PIX confirmado (#$orderId) — $brandName"
        val whenStr = efetivadoEm
            ?.atZoneSameInstant(ZoneId.of(appTz))
            ?.toLocalDateTime()
            ?.format(fmtDateTime)
            ?: ""

        val html = buildHtml(
            success = true,
            orderId = orderId,
            amount = amount,
            payeePixKey = key,
            idEnvio = idEnvio,
            endToEndId = endToEndId,
            txid = txid,
            whenStr = whenStr,
            errorCode = null,
            errorMsg = null,
            note = extraNote
        )

        val success = sendInternal(
            to = to,
            subject = subject,
            html = html,
            orderId = orderId,
            context = "CONFIRMED"
        )

        log.debug(
            "📧 PAYOUT PIX EMAIL [CONFIRMED]: resultado do envio - orderId={}, success={}, sandbox={}",
            orderId, success, sandbox
        )

        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_PIX,
            status = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED,
            errorMessage = if (!success) "Erro ao enviar e-mail (exceção capturada)" else null
        )
    }

    // ----------------------------------------------------------------
    // PÚBLICO: FALHA
    // ----------------------------------------------------------------

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
        val from = resolveFrom()

        log.info(
            "📧 PAYOUT PIX EMAIL [FAILED]: iniciando envio - orderId={}, to={}, sandbox={}, mailHost={}, errorCode={}",
            orderId, to, sandbox, mailHost, errorCode
        )
        log.debug(
            "📧 PAYOUT PIX EMAIL [FAILED]: config -> from={}, authorEmail={}, appTz={}, favoredKeyFromConfig={}",
            from, authorEmail, appTz, favoredKeyFromConfig
        )

        val key = (payeePixKey ?: favoredKeyFromConfig).orEmpty()
        val subject = "❌ Repasse PIX não realizado (#$orderId) — $brandName"
        val whenStr = triedAt
            ?.atZoneSameInstant(ZoneId.of(appTz))
            ?.toLocalDateTime()
            ?.format(fmtDateTime)
            ?: ""

        val html = buildHtml(
            success = false,
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

        val success = sendInternal(
            to = to,
            subject = subject,
            html = html,
            orderId = orderId,
            context = "FAILED"
        )

        log.debug(
            "📧 PAYOUT PIX EMAIL [FAILED]: resultado do envio de falha - orderId={}, success={}, sandbox={}",
            orderId, success, sandbox
        )

        val finalStatus = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED
        val finalErrorMessage = if (!success) {
            "Erro ao enviar e-mail de falha: $errorMsg"
        } else {
            errorMsg // mantém a mensagem original do repasse
        }

        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_PIX,
            status = finalStatus,
            errorMessage = finalErrorMessage
        )
    }

    // ----------------------------------------------------------------
    // CORE MAIL (envio real) - sem spring.mail.*, só env + mail.from
    // ----------------------------------------------------------------

    private fun sendInternal(
        to: String,
        subject: String,
        html: String,
        orderId: Long,
        context: String
    ): Boolean {
        val from = resolveFrom()
        val msg = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name())

        log.debug(
            "📧 PAYOUT PIX EMAIL [{}]: configurando MimeMessage - orderId={}, from={}, to={}, subject={}, sandbox={}, mailHost={}",
            context, orderId, from, to, subject, sandbox, mailHost
        )

        helper.setFrom(from, brandName)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)

        return try {
            log.info(
                "📧 PAYOUT PIX EMAIL [{}]: tentando enviar - orderId={}, sandbox={}",
                context, orderId, sandbox
            )
            mailSender.send(msg)
            log.info(
                "✅ PAYOUT PIX EMAIL [{}]: enviado com sucesso -> {} (orderId={}, sandbox={})",
                context, to, orderId, sandbox
            )
            true
        } catch (e: Exception) {
            log.error(
                "❌ PAYOUT PIX EMAIL [{}]: erro ao enviar para {} (orderId={}, sandbox={}, mailHost={}): {}",
                context, to, orderId, sandbox, mailHost, e.message, e
            )
            false
        }
    }

    // ----------------------------------------------------------------
    // BUSCA payout_id POR order_id
    // ----------------------------------------------------------------

    private fun findPayoutIdByOrderId(orderId: Long): Long? {
        return try {
            val row = jdbc.queryForMap(
                "SELECT id FROM payment_payouts WHERE order_id = :orderId LIMIT 1",
                mapOf("orderId" to orderId)
            )
            val id = (row["id"] as? Number)?.toLong()
            log.debug("PayoutEmail [PIX]: payout encontrado para orderId={} -> payoutId={}", orderId, id)
            id
        } catch (e: Exception) {
            log.warn("PayoutEmail [PIX]: payout não encontrado para orderId={}: {}", orderId, e.message)
            null
        }
    }

    // ----------------------------------------------------------------
    // PERSISTÊNCIA DE E-MAIL
    // ----------------------------------------------------------------

    private fun persistEmail(
        orderId: Long,
        to: String,
        emailType: PayoutEmailType,
        status: PayoutEmailStatus,
        errorMessage: String? = null
    ) {
        try {
            val payoutId = findPayoutIdByOrderId(orderId)

            val payoutEmail = PayoutEmail(
                payoutId = payoutId,
                orderId = orderId,
                toEmail = to,
                emailType = emailType.name,
                sentAt = OffsetDateTime.now(),
                status = status,
                errorMessage = errorMessage
            )

            payoutEmailRepository.save(payoutEmail)

            if (payoutId != null) {
                log.debug(
                    "PayoutEmail [PIX]: persistido (com payoutId) orderId={} payoutId={} type={} status={} error={}",
                    orderId, payoutId, emailType, status, errorMessage
                )
            } else {
                log.debug(
                    "PayoutEmail [PIX]: persistido (sem payoutId) orderId={} type={} status={} error={}",
                    orderId, emailType, status, errorMessage
                )
            }
        } catch (e: Exception) {
            log.error("PayoutEmail [PIX]: erro ao persistir e-mail para orderId={}: {}", orderId, e.message, e)
        }
    }

    // ----------------------------------------------------------------
    // HTML (sucesso / falha)
    // ----------------------------------------------------------------

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

        val statusLine = if (success) {
            "<p style=\"margin:0 0 6px\">🎉 <strong>Repasse PIX realizado com sucesso.</strong></p>"
        } else {
            "<p style=\"margin:0 0 6px\">❌ <strong>Repasse PIX não realizado.</strong></p>"
        }

        val cpfFmt = formatCpfIfPossible(payeePixKey)
        val favorecidoLine = if (cpfFmt != null) {
            "<p style=\"margin:6px 0\"><strong>👤 Favorecido (CPF):</strong> $cpfFmt</p>"
        } else {
            "<p style=\"margin:6px 0\"><strong>🎯 Favorecido (chave Pix):</strong> ${escape(payeePixKey)}</p>"
        }

        val extraOk = if (success) buildString {
            txid?.takeIf { it.isNotBlank() }?.let {
                append("<p style='margin:4px 0'><strong>🔑 TXID:</strong> ${escape(it)}</p>")
            }
            endToEndId?.takeIf { it.isNotBlank() }?.let {
                append("<p style='margin:4px 0'><strong>🔗 EndToEndId:</strong> ${escape(it)}</p>")
            }
        } else ""

        val extraErr = if (!success) {
            """
            <p style="margin:4px 0"><strong>Erro:</strong> ${escape(errorCode ?: "desconhecido")}</p>
            ${
                errorMsg?.let {
                    "<p style='margin:4px 0;color:#a00'>${escape(it)}</p>"
                } ?: ""
            }
            """.trimIndent()
        } else ""

        val noteBlock = note
            ?.takeIf { it.isNotBlank() }
            ?.let {
                """<p style="margin:10px 0 0"><strong>📝 Observação:</strong><br>${escape(it)}</p>"""
            } ?: ""

        val couponBlock = try {
            val order = orderRepository.findById(orderId).orElse(null)
            if (order?.couponCode != null &&
                order.discountAmount != null &&
                order.discountAmount!! > BigDecimal.ZERO
            ) {
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
            } else {
                ""
            }
        } catch (e: Exception) {
            log.warn("PayoutEmail [PIX]: erro ao buscar informações do cupom para pedido {}: {}", orderId, e.message)
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
                <strong>AndesCore Software</strong>
              </span>
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------

    /**
     * Resolve o FROM sem depender de spring.mail.*:
     * 1) MAIL_USERNAME (env)
     * 2) mail.from (configuredFrom)
     * 3) email do autor (authorEmail)
     */
    private fun resolveFrom(): String {
        val fromEnv = System.getenv("MAIL_USERNAME")?.takeIf { it.isNotBlank() }
        val fromConfig = configuredFrom.takeIf { it.isNotBlank() }
        val resolved = fromEnv ?: fromConfig ?: authorEmail

        log.debug(
            "📧 PayoutPixEmailService.resolveFrom(): resolvedFrom='{}' (env='{}', config='{}', author='{}')",
            resolved, fromEnv, fromConfig, authorEmail
        )

        return resolved
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun onlyDigits(s: String): String = s.filter { it.isDigit() }

    private fun formatCpfIfPossible(key: String?): String? {
        val d = onlyDigits(key.orEmpty())
        return if (d.length == 11) {
            "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
        } else {
            null
        }
    }
}
