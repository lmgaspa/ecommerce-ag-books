package com.luizgasparetto.backend.monolito.services.email.card

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.email.common.EmailFooter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets

/**
 * Classe base com funcionalidades compartilhadas para emails de Cartão
 */
abstract class CardEmailBase(
    protected val mailSender: JavaMailSender,
    protected val bookService: BookService,
    @Value("\${email.author}") val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") protected val brandName: String,
    @Value("\${mail.from:}") protected val configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") protected val logoUrl: String
) {
    protected val log = LoggerFactory.getLogger(javaClass)

    protected fun sendEmail(to: String, subject: String, html: String) {
        val msg = mailSender.createMimeMessage()
        val h = MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name())
        val from = (System.getenv("MAIL_USERNAME") ?: configuredFrom).ifBlank { authorEmail }
        h.setFrom(from, brandName)
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

    protected fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    protected fun onlyDigits(s: String): String = s.filter { it.isDigit() }

    protected fun normalizeBrPhone(digits: String): String =
        when {
            digits.length >= 13 && digits.startsWith("55") -> digits.takeLast(11)
            digits.length >= 11 -> digits.takeLast(11)
            else -> digits
        }

    protected fun maskCelularBr(src: String): String {
        val d = onlyDigits(src).let { normalizeBrPhone(it) }
        return when {
            d.length <= 2 -> "(${d}"
            d.length <= 7 -> "(${d.substring(0, 2)})${d.substring(2)}"
            d.length <= 11 -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7)}"
            else -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7, 11)}"
        }
    }

    protected fun buildAddressLine(order: Order): String {
        return buildString {
            append(escapeHtml(order.address))
            if (order.number.isNotBlank()) append(", nº ").append(escapeHtml(order.number))
            order.complement?.takeIf { it.isNotBlank() }?.let { append(" – ").append(escapeHtml(it)) }
            if (order.district.isNotBlank()) append(" – ").append(escapeHtml(order.district))
            append(", ${escapeHtml(order.city)} - ${escapeHtml(order.state)}, CEP ${escapeHtml(order.cep)}")
        }
    }

    protected fun buildItemsHtml(order: Order): String {
        return order.items.joinToString("") {
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
    }

    protected fun buildCouponBlock(order: Order, isAuthor: Boolean): String {
        if (order.couponCode == null || order.discountAmount == null || order.discountAmount!! <= BigDecimal.ZERO) {
            return ""
        }

        return if (isAuthor) {
            com.luizgasparetto.backend.monolito.services.email.cupom.author.CouponBlock.build(
                order.couponCode!!,
                order.discountAmount!!
            )
        } else {
            com.luizgasparetto.backend.monolito.services.email.cupom.client.CouponBlock.build(
                order.couponCode!!,
                order.discountAmount!!
            )
        }
    }

    protected fun buildInstallmentsInfo(order: Order): String {
        val n = (order.installments ?: 1)
        return if (n > 1) {
            val per = order.total.divide(BigDecimal(n), 2, RoundingMode.HALF_UP)
            """<p style="margin:6px 0"><strong>💳 Parcelado:</strong> $n× de R$ ${"%.2f".format(per.toDouble())} sem juros</p>"""
        } else {
            """<p style="margin:6px 0"><strong>💳 Pagamento à vista no cartão.</strong></p>"""
        }
    }

    protected fun buildContactBlock(): String {
        return """
            <p style="margin:16px 0 0;color:#555">
              Em caso de dúvida, fale com a <strong>$brandName</strong><br>
              ✉️ Email: <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> · 
              💬 WhatsApp: <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
            </p>
        """.trimIndent()
    }

    protected fun buildFooter(): String = EmailFooter.build()
}

