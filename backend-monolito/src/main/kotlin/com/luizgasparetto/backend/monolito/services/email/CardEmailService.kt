package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class CardEmailService(
    private val mailSender: JavaMailSender,
    private val bookService: BookService,
    @Value("\${email.author}") private val authorEmail: String
) {
    private val log = org.slf4j.LoggerFactory.getLogger(CardEmailService::class.java)

    // ------ aprovado ------
    fun sendCardClientEmail(order: Order) {
        sendEmail(
            to = order.email,
            subject = "✅ Cartão aprovado (#${order.id}) — Agenor Gasparetto - E-Commerce",
            html = buildHtmlMessage(order, isAuthor = false, declined = false)
        )
    }

    fun sendCardAuthorEmail(order: Order) {
        sendEmail(
            to = authorEmail,
            subject = "📦 Novo pedido pago (cartão) (#${order.id}) — Agenor Gasparetto - E-Commerce",
            html = buildHtmlMessage(order, isAuthor = true, declined = false)
        )
    }

    // ------ recusado ------
    fun sendClientCardDeclined(order: Order) {
        sendEmail(
            to = order.email,
            subject = "❌ Cartão não aprovado (#${order.id}) — Agenor Gasparetto - E-Commerce",
            html = buildHtmlMessage(order, isAuthor = false, declined = true)
        )
    }

    fun sendAuthorCardDeclined(order: Order) {
        sendEmail(
            to = authorEmail,
            subject = "⚠️ Pedido recusado no cartão (#${order.id}) — Agenor Gasparetto - E-Commerce",
            html = buildHtmlMessage(order, isAuthor = true, declined = true)
        )
    }

    // ---------------- core ----------------

    private fun sendEmail(to: String, subject: String, html: String) {
        val msg = mailSender.createMimeMessage()
        val h = MimeMessageHelper(msg, true, "UTF-8")
        val from = System.getenv("MAIL_USERNAME") ?: authorEmail
        h.setFrom(from)
        h.setTo(to)
        h.setSubject(subject)
        h.setText(html, true)

        try {
            mailSender.send(msg)
            log.info("MAIL enviado OK -> {}", to)
        } catch (e: Exception) {
            log.error("MAIL ERRO para {}: {}", to, e.message, e)
        }
    }

    private fun buildHtmlMessage(order: Order, isAuthor: Boolean, declined: Boolean): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > java.math.BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val phoneDigits = onlyDigits(order.phone)
        val nationalPhone = normalizeBrPhone(phoneDigits)
        val maskedPhone = maskCelularBr(nationalPhone.ifEmpty { order.phone })
        val waHref = if (nationalPhone.length == 11) "https://wa.me/55$nationalPhone"
        else "https://wa.me/55$phoneDigits"

        val itemsHtml = order.items.joinToString("") {
            val img = bookService.getImageUrl(it.bookId)
            """
            <tr>
              <td style="padding:12px 0;border-bottom:1px solid #eee;">
                <table cellpadding="0" cellspacing="0" style="border-collapse:collapse">
                  <tr>
                    <td><img src="$img" alt="${it.title}" width="70" style="border-radius:8px;vertical-align:middle;margin-right:12px"></td>
                    <td style="padding-left:12px">
                      <div style="font-weight:600">${it.title}</div>
                      <div style="color:#555;font-size:12px">${it.quantity}× — R$ ${"%.2f".format(it.price.toDouble())}</div>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            """.trimIndent()
        }

        val addressLine = buildString {
            append(order.address)
            if (order.number.isNotBlank()) append(", nº ").append(order.number)
            order.complement?.takeIf { it.isNotBlank() }?.let { append(" – ").append(it) }
            if (order.district.isNotBlank()) append(" – ").append(order.district)
            append(", ${order.city} - ${order.state}, CEP ${order.cep}")
        }

        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>"""
        } ?: ""

        // Parcelas (somente cartão e se aprovado)
        val installmentsInfo =
            if (!declined) {
                val n = (order.installments ?: 1)
                if (n > 1) {
                    val per = order.total.divide(java.math.BigDecimal(n), 2, java.math.RoundingMode.HALF_UP)
                    "<p style=\"margin:6px 0\"><strong>💳 Parcelado:</strong> $n× de R$ %.2f sem juros</p>".format(per.toDouble())
                } else {
                    "<p style=\"margin:6px 0\"><strong>💳 Pagamento à vista no cartão.</strong></p>"
                }
            } else ""

        val headerClient = if (declined) {
            """
            <p style="margin:0 0 12px">Olá, <strong>${order.firstName} ${order.lastName}</strong>.</p>
            <p style="margin:0 0 6px">❌ <strong>Seu pagamento no cartão foi recusado.</strong></p>
            <p style="margin:0 0 6px">Tente novamente com outro cartão, confirme dados/limite ou opte por Pix.</p>
            <p style="margin:0 0 6px">📍 Endereço de entrega: $addressLine</p>
            $noteBlock
            """.trimIndent()
        } else {
            """
            <p style="margin:0 0 12px">Olá, <strong>${order.firstName} ${order.lastName}</strong>!</p>
            <p style="margin:0 0 6px">🎉 <strong>Recebemos o seu pagamento no cartão.</strong> Seu pedido foi CONFIRMED.</p>
            <p style="margin:0 0 6px">📍 Endereço de entrega: $addressLine</p>
            $noteBlock
            """.trimIndent()
        }

        val headerAuthor = if (declined) {
            """
            <p style="margin:0 0 10px"><strong>⚠️ Pedido recusado no cartão</strong>.</p>
            <p style="margin:0 0 4px">👤 Cliente: ${order.firstName} ${order.lastName}</p>
            <p style="margin:0 0 4px">✉️ Email: ${order.email}</p>
            <p style="margin:0 0 4px">📱 WhatsApp (cliente): <a href="$waHref">$maskedPhone</a></p>
            <p style="margin:0 0 4px">📍 Endereço: $addressLine</p>
            <p style="margin:0 0 4px">💳 Método: Cartão de crédito (recusado)</p>
            $noteBlock
            """.trimIndent()
        } else {
            """
            <p style="margin:0 0 10px"><strong>📦 Novo pedido pago</strong> no site.</p>
            <p style="margin:0 0 4px">👤 Cliente: ${order.firstName} ${order.lastName}</p>
            <p style="margin:0 0 4px">✉️ Email: ${order.email}</p>
            <p style="margin:0 0 4px">📱 WhatsApp (cliente): <a href="$waHref">$maskedPhone</a></p>
            <p style="margin:0 0 4px">📍 Endereço: $addressLine</p>
            <p style="margin:0 0 4px">💳 Método: Cartão de crédito</p>
            $noteBlock
            """.trimIndent()
        }

        val who = if (isAuthor) headerAuthor else headerClient
        val contactBlock = if (!isAuthor) """
            <p style="margin:16px 0 0;color:#555">
              Em caso de dúvida, fale com a <strong>Agenor Gasparetto - E-Commerce</strong><br>
              ✉️ Email: <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> · 
              💬 WhatsApp: <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
            </p>
        """.trimIndent() else ""

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:12px;overflow:hidden">
            <div style="background:#0b0b0c;color:#fff;padding:16px 20px;display:flex;align-items:center;gap:10px">
              <span style="font-size:18px">📚</span>
              <strong style="font-size:16px">Agenor Gasparetto - E-Commerce</strong>
            </div>
            <div style="padding:20px">
              $who

              <p style="margin:12px 0 8px"><strong>🧾 Nº do pedido:</strong> #${order.id}</p>

              ${if (!declined) """
              <h3 style="font-size:15px;margin:16px 0 8px">🛒 Itens</h3>
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                $itemsHtml
              </table>

              <div style="margin-top:14px">
                <p style="margin:4px 0">🚚 <strong>Frete:</strong> $shipping</p>
                <p style="margin:4px 0;font-size:16px">💰 <strong>Total:</strong> $total</p>
                <p style="margin:4px 0">💳 <strong>Pagamento:</strong> Cartão de crédito</p>
                $installmentsInfo
              </div>

              ${if (!isAuthor) "<p style=\"margin:16px 0 0\">Obrigado por comprar com a gente! 💛</p>" else ""}

              """ else """
              <p style="margin:16px 0 0">Você pode tentar novamente com outro cartão ou optar por pagamento via Pix (liberação rápida) 💡</p>
              """}

              $contactBlock
            </div>
            <div style="background:#fafafa;color:#888;padding:12px 20px;text-align:center;font-size:12px">
              © ${java.time.Year.now()} Agenor Gasparetto - E-Commerce · Todos os direitos direitos Reservados· ✉️ <a href="mailto:luhmgasparetto@gmail.com" style="color:#888;text-decoration:none">luhmgasparetto@gmail.com</a>
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    // Helpers (locais)
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
            d.length <= 11 -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7)}"
            else -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7, 11)}"
        }
    }
    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
