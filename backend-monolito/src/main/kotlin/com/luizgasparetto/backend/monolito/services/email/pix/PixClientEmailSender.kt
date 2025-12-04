package com.luizgasparetto.backend.monolito.services.email.pix

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderEmailStatus
import com.luizgasparetto.backend.monolito.models.order.OrderEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderEmailRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.email.order.OrderEmailHelper
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Responsável por enviar email para cliente quando Pix é confirmado
 */
@Component
class PixClientEmailSender(
    mailSender: JavaMailSender,
    bookService: BookService,
    private val orderEmailRepository: OrderEmailRepository,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String
) : PixEmailBase(mailSender, bookService, authorEmail, brandName, configuredFrom, logoUrl) {

    fun send(order: Order) {
        val subject = "✅ Pagamento CONFIRMADO (#${order.id}) — $brandName"
        val html = buildHtml(order)
        
        try {
            sendEmail(to = order.email, subject = subject, html = html)
            order.id?.let { 
                OrderEmailHelper.persistEmail(
                    repository = orderEmailRepository,
                    orderId = it,
                    to = order.email,
                    emailType = OrderEmailType.PAID,
                    status = OrderEmailStatus.SENT
                )
            }
        } catch (e: Exception) {
            order.id?.let { 
                OrderEmailHelper.persistEmail(
                    repository = orderEmailRepository,
                    orderId = it,
                    to = order.email,
                    emailType = OrderEmailType.PAID,
                    status = OrderEmailStatus.FAILED,
                    errorMessage = e.message
                )
            }
            throw e
        }
    }

    private fun buildHtml(order: Order): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val addressLine = buildAddressLine(order)
        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>"""
        } ?: ""

        val header = """
            <p style="margin:0 0 12px">Olá, <strong>${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</strong>!</p>
            <p style="margin:0 0 6px">🎉 <strong>Recebemos o seu pagamento via Pix.</strong> Seu pedido foi CONFIRMADO.</p>
            <p style="margin:0 0 6px">📍 Endereço de entrega: $addressLine</p>
            $noteBlock
        """.trimIndent()

        val txidLine = order.txid?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:6px 0"><strong>🔑 TXID Pix:</strong> ${escapeHtml(it)}</p>"""
        } ?: ""

        val itemsHtml = buildItemsHtml(order)
        val couponBlock = buildCouponBlock(order, isAuthor = false)
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
                    <div style="opacity:.9;font-size:12px;line-height:1.2;margin-top:4px;">Pagamento confirmado</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              $header

              <p style="margin:12px 0 8px"><strong>🧾 Nº do pedido:</strong> #${escapeHtml(order.id.toString())}</p>
              $txidLine

              $couponBlock

              <h3 style="font-size:15px;margin:16px 0 8px">🛒 Itens</h3>
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                $itemsHtml
              </table>

              <div style="margin-top:14px">
                <p style="margin:4px 0">🚚 <strong>Frete:</strong> $shipping</p>
                <p style="margin:4px 0;font-size:16px">💰 <strong>Total:</strong> $total</p>
                <p style="margin:4px 0">💳 <strong>Pagamento:</strong> Pix</p>
              </div>

              <p style="margin:16px 0 0">Obrigado por comprar com a gente! 💛</p>

              $contactBlock
            </div>

            $footer
          </div>
        </body>
        </html>
        """.trimIndent()
    }
}

