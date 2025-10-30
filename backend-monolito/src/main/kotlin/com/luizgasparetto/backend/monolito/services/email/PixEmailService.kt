package com.luizgasparetto.backend.monolito.services.email

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Year
import java.nio.charset.StandardCharsets

@Service
class PixEmailService(
    private val mailSender: JavaMailSender,
    private val bookService: BookService,
    @Value("\${email.author}") private val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") private val brandName: String,
    // remetente opcional configurável; se vazio, tenta MAIL_USERNAME; por fim, authorEmail
    @Value("\${mail.from:}") private val configuredFrom: String,
    // **modelo Welcome**: usar logo externa para não gerar anexo
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br.jpg}") private val logoUrl: String
) {
    private val log = LoggerFactory.getLogger(PixEmailService::class.java)

    fun sendPixClientEmail(order: Order) {
        sendEmail(
            to = order.email,
            subject = "✅ Pagamento CONFIRMADO (#${order.id}) — $brandName",
            html = buildHtmlMessage(order, isAuthor = false)
        )
    }

    fun sendPixAuthorEmail(order: Order) {
        sendEmail(
            to = authorEmail,
            subject = "📦 Novo pedido pago (#${order.id}) — $brandName",
            html = buildHtmlMessage(order, isAuthor = true)
        )
    }

    // ---------------- core (modelo Welcome: sem anexos/CID) ----------------
    private fun sendEmail(to: String, subject: String, html: String) {
        val msg = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(msg, /* multipart = */ false, StandardCharsets.UTF_8.name())

        val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
        helper.setFrom(from, brandName)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)

        try {
            mailSender.send(msg)
            log.info("MAIL enviado OK -> {}", to)
        } catch (e: Exception) {
            log.error("MAIL ERRO para {}: {}", to, e.message, e)
        }
    }

    // ---------------- HTML ----------------
    private fun buildHtmlMessage(order: Order, isAuthor: Boolean): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val phoneDigits = onlyDigits(order.phone)
        val nationalPhone = normalizeBrPhone(phoneDigits)
        val maskedPhone = maskCelularBr(nationalPhone.ifEmpty { order.phone })
        val waHref = if (nationalPhone.length == 11) "https://wa.me/55$nationalPhone" else "https://wa.me/55$phoneDigits"

        val itemsHtml = order.items.joinToString("") {
            val img = bookService.getImageUrl(it.bookId)
            """
            <tr>
              <td style="padding:12px 0;border-bottom:1px solid #eee;">
                <table cellpadding="0" cellspacing="0" style="border-collapse:collapse">
                  <tr>
                    <td><img src="$img" alt="${escapeHtml(it.title)}" width="70" style="border-radius:8px;vertical-align:middle;margin-right:12px"></td>
                    <td style="padding-left:12px">
                      <div style="font-weight:600">${escapeHtml(it.title)}</div>
                      <div style="color:#555;font-size:12px">${it.quantity}× — R$ ${"%.2f".format(it.price.toDouble())}</div>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            """.trimIndent()
        }

        val addressLine = buildString {
            append(escapeHtml(order.address))
            if (order.number.isNotBlank()) append(", nº ").append(escapeHtml(order.number))
            order.complement?.takeIf { it.isNotBlank() }?.let { append(" – ").append(escapeHtml(it)) }
            if (order.district.isNotBlank()) append(" – ").append(escapeHtml(order.district))
            append(", ${escapeHtml(order.city)} - ${escapeHtml(order.state)}, CEP ${escapeHtml(order.cep)}")
        }

        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>"""
        } ?: ""

        val headerClient = """
            <p style="margin:0 0 12px">Olá, <strong>${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</strong>!</p>
            <p style="margin:0 0 6px">🎉 <strong>Recebemos o seu pagamento via Pix.</strong> Seu pedido foi CONFIRMADO.</p>
            <p style="margin:0 0 6px">📍 Endereço de entrega: $addressLine</p>
            $noteBlock
        """.trimIndent()

        val headerAuthor = """
            <p style="margin:0 0 10px"><strong>📦 Novo pedido pago</strong> no site.</p>
            <p style="margin:0 0 4px">👤 Cliente: ${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</p>
            <p style="margin:0 0 4px">✉️ Email: ${escapeHtml(order.email)}</p>
            <p style="margin:0 0 4px">📱 WhatsApp (cliente): <a href="$waHref">$maskedPhone</a></p>
            <p style="margin:0 0 4px">📍 Endereço: $addressLine</p>
            <p style="margin:0 0 4px">💳 Método: Pix</p>
            $noteBlock
        """.trimIndent()

        val txidLine = order.txid?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:6px 0"><strong>🔑 TXID Pix:</strong> ${escapeHtml(it)}</p>"""
        } ?: ""

        val who = if (isAuthor) headerAuthor else headerClient
        val subtitle = if (isAuthor) "Novo pedido pago" else "Pagamento confirmado"

        val contactBlock = if (!isAuthor) """
            <p style="margin:16px 0 0;color:#555">
              Em caso de dúvida, fale com a <strong>$brandName</strong><br>
              ✉️ Email: <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> · 
              💬 WhatsApp: <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
            </p>
        """.trimIndent() else ""

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:12px;overflow:hidden">

            <!-- HEADER (modelo Welcome: logo por URL externa, sem CID) -->
            <div style="background:linear-gradient(135deg,#0a2239,#0e4b68);color:#fff;padding:16px 20px;">
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                <tr>
                  <td style="width:64px;vertical-align:middle;">
                    <img src="$logoUrl" alt="${escapeHtml(brandName)}" width="56" style="display:block;border-radius:6px;">
                  </td>
                  <td style="text-align:right;vertical-align:middle;">
                    <div style="font-weight:700;font-size:18px;line-height:1;">${escapeHtml(brandName)}</div>
                    <div style="height:6px;line-height:6px;font-size:0;">&nbsp;</div>
                    <div style="opacity:.9;font-size:12px;line-height:1.2;margin-top:4px;">$subtitle</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              $who

              <p style="margin:12px 0 8px"><strong>🧾 Nº do pedido:</strong> #${escapeHtml(order.id.toString())}</p>
              $txidLine

              ${if (order.couponCode != null && order.discountAmount != null && order.discountAmount!! > BigDecimal.ZERO) {
                val couponCode = order.couponCode!!
                val discountAmount = order.discountAmount!!
                val discountFormatted = "R$ %.2f".format(discountAmount.toDouble())
                if (isAuthor) {
                  """
                  <!-- CUPOM APLICADO - AUTOR -->
                  <div style="background:#fff3cd;border:1px solid #ffeaa7;border-radius:8px;padding:16px;margin:16px 0;text-align:center;">
                    <div style="color:#856404;font-size:24px;margin-bottom:8px;">🎫</div>
                    <div style="font-weight:700;color:#856404;font-size:16px;margin-bottom:4px;">CUPOM UTILIZADO</div>
                    <div style="font-weight:600;color:#856404;font-size:14px;margin-bottom:8px;">Código: ${escapeHtml(couponCode)}</div>
                    <div style="font-weight:700;color:#856404;font-size:18px;">Pagamento reduzido em $discountFormatted</div>
                  </div>
                  """.trimIndent()
                } else {
                  """
                  <!-- CUPOM APLICADO - CLIENTE -->
                  <div style="background:#f8f9fa;border:1px solid #dee2e6;border-radius:8px;padding:16px;margin:16px 0;text-align:center;">
                    <div style="color:#28a745;font-size:24px;margin-bottom:8px;">🎯</div>
                    <div style="font-weight:700;color:#495057;font-size:16px;margin-bottom:4px;">CUPOM APLICADO</div>
                    <div style="font-weight:600;color:#6c757d;font-size:14px;margin-bottom:8px;">Código: ${escapeHtml(couponCode)}</div>
                    <div style="font-weight:700;color:#28a745;font-size:18px;">Você economizou $discountFormatted! 💰</div>
                  </div>
                  """.trimIndent()
                }
              } else ""}

              <h3 style="font-size:15px;margin:16px 0 8px">🛒 Itens</h3>
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                $itemsHtml
              </table>

              <div style="margin-top:14px">
                <p style="margin:4px 0">🚚 <strong>Frete:</strong> $shipping</p>
                <p style="margin:4px 0;font-size:16px">💰 <strong>Total:</strong> $total</p>
                <p style="margin:4px 0">💳 <strong>Pagamento:</strong> Pix</p>
              </div>

              ${if (!isAuthor) "<p style=\"margin:16px 0 0\">Obrigado por comprar com a gente! 💛</p>" else ""}

              $contactBlock
            </div>

            <!-- FOOTER (raio FE0E; sem anexo) -->
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

    // ---------------- Helpers ----------------
    private fun onlyDigits(s: String): String = s.filter { it.isDigit() }

    private fun normalizeBrPhone(digits: String): String =
        when {
            digits.length >= 13 && digits.startsWith("55") -> digits.takeLast(11)
            digits.length >= 11 -> digits.takeLast(11)
            else -> digits
        }

    private fun maskCelularBr(src: String): String {
        val d = onlyDigits(src).let { normalizeBrPhone(it) }
        return when {
            d.length <= 2 -> "(${d}"
            d.length <= 7 -> "(${d.substring(0, 2)})${d.substring(2)}"
            d.length <= 11 -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7, 11)}"
            else -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7, 11)}"
        }
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
