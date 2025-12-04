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
 * Responsável por enviar email para cliente quando pagamento é rejeitado (FAILED/DECLINED)
 */
@Component
class OrderFailedEmailSender(
    mailSender: JavaMailSender,
    bookService: BookService,
    orderEmailRepository: OrderEmailRepository,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String
) : OrderStatusEmailBase(mailSender, bookService, orderEmailRepository, authorEmail, brandName, configuredFrom, logoUrl) {

    fun send(order: Order, reason: String? = null) {
        val subject = "⚠️ Pagamento não aprovado - Pedido #${order.id} — $brandName"
        val html = buildHtml(order, reason)
        
        try {
            sendEmail(to = order.email, subject = subject, html = html)
            order.id?.let { 
                persistEmail(
                    orderId = it,
                    to = order.email,
                    emailType = OrderEmailType.FAILED,
                    status = OrderEmailStatus.SENT
                )
            }
        } catch (e: Exception) {
            order.id?.let { 
                persistEmail(
                    orderId = it,
                    to = order.email,
                    emailType = OrderEmailType.FAILED,
                    status = OrderEmailStatus.FAILED,
                    errorMessage = e.message
                )
            }
            throw e
        }
    }

    private fun buildHtml(order: Order, reason: String?): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val paymentMethod = when (order.paymentMethod.lowercase()) {
            "card", "cartao" -> "Cartão de Crédito"
            "pix" -> "Pix"
            else -> "Pagamento"
        }

        val reasonBlock = reason?.takeIf { it.isNotBlank() }?.let {
            """<p style="margin:8px 0;padding:12px;background:#fff3cd;border-left:4px solid #ffc107;border-radius:4px;color:#856404;">
               <strong>Motivo:</strong> ${escapeHtml(it)}
               </p>"""
        } ?: ""

        val header = """
            <p style="margin:0 0 12px">Olá, <strong>${escapeHtml(order.firstName)} ${escapeHtml(order.lastName)}</strong>!</p>
            <p style="margin:0 0 6px">⚠️ <strong>Infelizmente, o pagamento do seu pedido #${order.id} não foi aprovado.</strong></p>
            <p style="margin:0 0 6px">Não se preocupe! Você pode tentar novamente realizando um novo pedido.</p>
            $reasonBlock
        """.trimIndent()

        val itemsHtml = buildItemsHtml(order)
        val couponBlock = buildCouponBlock(order)
        val contactBlock = buildContactBlock()
        val footer = buildFooter()

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:12px;overflow:hidden">

            <!-- HEADER -->
            <div style="background:linear-gradient(135deg,#dc3545,#c82333);color:#fff;padding:16px 20px;">
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                <tr>
                  <td style="width:64px;vertical-align:middle;">
                    <img src="$logoUrl" alt="${escapeHtml(brandName)}" width="56" style="display:block;border-radius:6px;">
                  </td>
                  <td style="text-align:right;vertical-align:middle;">
                    <div style="font-weight:700;font-size:18px;line-height:1;">${escapeHtml(brandName)}</div>
                    <div style="height:6px;line-height:6px;font-size:0;">&nbsp;</div>
                    <div style="opacity:.9;font-size:12px;line-height:1.2;">Pagamento não aprovado</div>
                  </td>
                </tr>
              </table>
            </div>

            <div style="padding:20px">
              $header

              <p style="margin:12px 0 8px"><strong>🧾 Nº do pedido:</strong> #${escapeHtml(order.id.toString())}</p>

              $couponBlock

              <h3 style="font-size:15px;margin:16px 0 8px">🛒 Itens do pedido</h3>
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                $itemsHtml
              </table>

              <div style="margin-top:14px">
                <p style="margin:4px 0;font-size:16px">💰 <strong>Total:</strong> $total</p>
                <p style="margin:4px 0">💳 <strong>Método de pagamento:</strong> $paymentMethod</p>
              </div>

              <div style="margin-top:20px;padding:16px;background:#f8f9fa;border-radius:8px;border:1px solid #dee2e6;">
                <p style="margin:0 0 8px;font-weight:600;color:#495057;">💡 O que fazer agora?</p>
                <ul style="margin:0;padding-left:20px;color:#6c757d;">
                  <li>Verifique os dados do seu $paymentMethod</li>
                  <li>Confirme se há saldo ou limite disponível</li>
                  <li>Tente realizar um novo pedido</li>
                  <li>Entre em contato conosco se precisar de ajuda</li>
                </ul>
              </div>

              <p style="margin:16px 0 0">Estamos à disposição para ajudar! 💛</p>

              $contactBlock
            </div>

            $footer
          </div>
        </body>
        </html>
        """.trimIndent()
    }
}

