package com.luizgasparetto.backend.monolito.services.email.order

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderEmailStatus
import com.luizgasparetto.backend.monolito.models.order.OrderEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderEmailRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Responsável por enviar email para cliente quando pedido é criado (status PENDING/WAITING)
 */
@Component
class OrderPendingEmailSender(
    mailSender: JavaMailSender,
    bookService: BookService,
    orderEmailRepository: OrderEmailRepository,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String
) : OrderStatusEmailBase(mailSender, bookService, orderEmailRepository, authorEmail, brandName, configuredFrom, logoUrl) {

    fun send(order: Order) {
        val paymentMethod = when (order.paymentMethod.lowercase()) {
            "card", "cartao" -> "Cartão de Crédito"
            "pix" -> "Pix"
            else -> "Pagamento"
        }
        
        val subject = "📦 Recebemos seu pedido #${order.id} — $brandName"
        val html = buildHtml(order, paymentMethod)
        
        try {
            sendEmail(to = order.email, subject = subject, html = html)
            order.id?.let { 
                persistEmail(
                    orderId = it,
                    to = order.email,
                    emailType = OrderEmailType.PENDING,
                    status = OrderEmailStatus.SENT
                )
            }
        } catch (e: Exception) {
            order.id?.let { 
                persistEmail(
                    orderId = it,
                    to = order.email,
                    emailType = OrderEmailType.PENDING,
                    status = OrderEmailStatus.FAILED,
                    errorMessage = e.message
                )
            }
            throw e
        }
    }

    private fun buildHtml(order: Order, paymentMethod: String): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val addressLine = buildAddressLine(order)
        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:10px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>"""
        } ?: ""

        val header = """
            <p style="margin:0 0 12px">Olá, <strong>${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</strong>!</p>
            <p style="margin:0 0 6px">✅ <strong>Recebemos seu pedido #${order.id}!</strong></p>
            <p style="margin:0 0 6px">⏳ Estamos aguardando a aprovação do pagamento via <strong>$paymentMethod</strong>.</p>
            <p style="margin:0 0 6px">📍 Endereço de entrega: $addressLine</p>
            $noteBlock
        """.trimIndent()

        val itemsHtml = buildItemsHtml(order)
        val couponBlock = buildCouponBlock(order)
        val contactBlock = buildContactBlock()
        val footer = buildFooter()

        val paymentInfo = when (order.paymentMethod.lowercase()) {
            "card", "cartao" -> {
                val installments = order.installments ?: 1
                if (installments > 1) {
                    val per = order.total.divide(BigDecimal(installments), 2, java.math.RoundingMode.HALF_UP)
                    """<p style="margin:4px 0">💳 <strong>Pagamento:</strong> $paymentMethod (${installments}× de R$ ${"%.2f".format(per.toDouble())})</p>"""
                } else {
                    """<p style="margin:4px 0">💳 <strong>Pagamento:</strong> $paymentMethod</p>"""
                }
            }
            "pix" -> {
                """<p style="margin:4px 0">💳 <strong>Pagamento:</strong> $paymentMethod</p>
                   <p style="margin:4px 0;color:#666;font-size:13px">💡 <em>Você receberá um email assim que o pagamento for confirmado.</em></p>"""
            }
            else -> """<p style="margin:4px 0">💳 <strong>Pagamento:</strong> $paymentMethod</p>"""
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
                    <img src="$logoUrl" alt="${escapeHtml(brandName)}" width="56" style="display:block;border-radius:6px;">
                  </td>
                  <td style="text-align:right;vertical-align:middle;">
                    <div style="font-weight:700;font-size:18px;line-height:1;">${escapeHtml(brandName)}</div>
                    <div style="height:6px;line-height:6px;font-size:0;">&nbsp;</div>
                    <div style="opacity:.9;font-size:12px;line-height:1.2;">Pedido recebido</div>
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
                $paymentInfo
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

