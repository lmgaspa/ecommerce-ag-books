package com.luizgasparetto.backend.monolito.services.email.card

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Responsável por enviar email para autor quando cartão é aprovado
 */
@Component
class CardAuthorApprovedEmailSender(
    mailSender: JavaMailSender,
    bookService: BookService,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String
) : CardEmailBase(mailSender, bookService, authorEmail, brandName, configuredFrom, logoUrl) {

    fun send(order: Order) {
        val subject = "📦 Novo pedido pago (cartão) (#${order.id}) — $brandName"
        val html = buildHtml(order)
        sendEmail(to = authorEmail, subject = subject, html = html)
    }

    private fun buildHtml(order: Order): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val phoneDigits = onlyDigits(order.phone)
        val nationalPhone = normalizeBrPhone(phoneDigits)
        val maskedPhone = maskCelularBr(nationalPhone.ifEmpty { order.phone })
        val waHref = if (nationalPhone.length == 11) "https://wa.me/55$nationalPhone" else "https://wa.me/55$phoneDigits"

        val addressLine = buildAddressLine(order)
        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>"""
        } ?: ""

        val header = """
            <p style="margin:0 0 10px"><strong>📦 Novo pedido pago</strong> no site.</p>
            <p style="margin:0 0 4px">👤 Cliente: ${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</p>
            <p style="margin:0 0 4px">✉️ Email: ${escapeHtml(order.email)}</p>
            <p style="margin:0 0 4px">📱 WhatsApp (cliente): <a href="$waHref">$maskedPhone</a></p>
            <p style="margin:0 0 4px">📍 Endereço: $addressLine</p>
            <p style="margin:0 0 4px">💳 Método: Cartão de crédito</p>
            $noteBlock
        """.trimIndent()

        val itemsHtml = buildItemsHtml(order)
        val couponBlock = buildCouponBlock(order, isAuthor = true)
        val installmentsInfo = buildInstallmentsInfo(order)
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
                    <div style="opacity:.9;font-size:12px;line-height:1.2;">Novo pedido pago (cartão)</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              $header

              <p style="margin:12px 0 8px"><strong>🧾 Nº do pedido:</strong> #${escapeHtml(order.id.toString())}</p>

              $couponBlock

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
            </div>

            $footer
          </div>
        </body>
        </html>
        """.trimIndent()
    }
}

