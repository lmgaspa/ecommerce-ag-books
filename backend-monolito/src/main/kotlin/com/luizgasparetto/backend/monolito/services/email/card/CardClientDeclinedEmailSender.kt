package com.luizgasparetto.backend.monolito.services.email.card

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * Responsável por enviar email para cliente quando cartão é recusado
 */
@Component
class CardClientDeclinedEmailSender(
    mailSender: JavaMailSender,
    bookService: BookService,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String
) : CardEmailBase(mailSender, bookService, authorEmail, brandName, configuredFrom, logoUrl) {

    fun send(order: Order) {
        val subject = "❌ Cartão não aprovado (#${order.id}) — $brandName"
        val html = buildHtml(order)
        sendEmail(to = order.email, subject = subject, html = html)
    }

    private fun buildHtml(order: Order): String {
        val addressLine = buildAddressLine(order)
        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>"""
        } ?: ""

        val header = """
            <p style="margin:0 0 12px">Olá, <strong>${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</strong>.</p>
            <p style="margin:0 0 6px">❌ <strong>Seu pagamento no cartão foi recusado.</strong></p>
            <p style="margin:0 0 6px">Tente novamente com outro cartão, confirme dados/limite ou opte por Pix.</p>
            <p style="margin:0 0 6px">📍 Endereço de entrega: $addressLine</p>
            $noteBlock
        """.trimIndent()

        val contactBlock = buildContactBlock()
        val footer = buildFooter()

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:12px;overflow:hidden">

            <!-- HEADER -->
            <div style="background:linear-gradient(135deg,#0a2239,#0e4b68);color:#fff;padding:16px 20px;">
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                <tr>
                  <td style="width:64px;vertical-align:middle;">
                    <img src="$logoUrl" alt="${escapeHtml(brandName)}" width="56" style="display:block;border-radius:6px;">
                  </td>
                  <td style="text-align:right;vertical-align:middle;">
                    <div style="font-weight:700;font-size:18px;line-height:1;">${escapeHtml(brandName)}</div>
                    <div style="height:6px;line-height:6px;font-size:0;">&nbsp;</div>
                    <div style="opacity:.9;font-size:12px;line-height:1.2;">Cartão não aprovado</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              $header

              <p style="margin:12px 0 8px"><strong>🧾 Nº do pedido:</strong> #${escapeHtml(order.id.toString())}</p>

              <p style="margin:16px 0 0">Você pode tentar novamente com outro cartão ou optar por pagamento via Pix (liberação rápida) 💡</p>

              $contactBlock
            </div>

            $footer
          </div>
        </body>
        </html>
        """.trimIndent()
    }
}

