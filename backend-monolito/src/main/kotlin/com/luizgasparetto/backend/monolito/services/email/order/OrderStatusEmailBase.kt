package com.luizgasparetto.backend.monolito.services.email.order

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderEmail
import com.luizgasparetto.backend.monolito.models.order.OrderEmailStatus
import com.luizgasparetto.backend.monolito.models.order.OrderEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderEmailRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.email.common.EmailFooter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

/**
 * Classe base com funcionalidades compartilhadas para emails de status de pedido.
 *
 * Responsabilidades:
 * - Montar HTML dos emails
 * - Enviar email via JavaMailSender
 * - Persistir auditoria em order_email (transação própria)
 */
abstract class OrderStatusEmailBase(
    protected val mailSender: JavaMailSender,
    protected val bookService: BookService,
    protected val orderEmailRepository: OrderEmailRepository,
    @Value("\${email.author}") val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") protected val brandName: String,
    @Value("\${mail.from:}") protected val configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") protected val logoUrl: String
) {
    protected val log = LoggerFactory.getLogger(javaClass)

    protected fun sendEmail(to: String, subject: String, html: String) {
        val msg = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name())

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
            throw e
        }
    }

    /**
     * Persiste o registro de email em uma transação separada (REQUIRES_NEW)
     * para garantir que o pedido já esteja commitado no banco de dados.
     *
     * IMPORTANTE:
     * - Este método NUNCA lança exceções para o chamador.
     * - Qualquer falha na auditoria é logada, mas não quebra o fluxo principal
     *   (nem o listener de eventos, nem o checkout).
     */
    protected fun persistEmail(
        orderId: Long,
        to: String,
        emailType: OrderEmailType,
        status: OrderEmailStatus,
        errorMessage: String? = null
    ) {
        runCatching {
            persistEmailTx(orderId, to, emailType, status, errorMessage)
        }.onFailure { e ->
            log.error(
                "OrderEmail: erro ao persistir e-mail para orderId={} type={} status={}: {}",
                orderId, emailType, status, e.message, e
            )
        }
    }

    /**
     * Método realmente transacional, isolado em REQUIRES_NEW.
     * Se algo der errado aqui, apenas esta transação é revertida.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected fun persistEmailTx(
        orderId: Long,
        to: String,
        emailType: OrderEmailType,
        status: OrderEmailStatus,
        errorMessage: String? = null
    ) {
        val orderEmail = OrderEmail(
            orderId = orderId,
            toEmail = to,
            emailType = emailType.name,
            sentAt = OffsetDateTime.now(),
            status = status,
            errorMessage = errorMessage
        )

        orderEmailRepository.save(orderEmail)

        log.debug(
            "OrderEmail: persistido orderId={} type={} status={} error={}",
            orderId, emailType, status, errorMessage
        )
    }

    protected fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

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

    protected fun buildCouponBlock(order: Order): String {
        if (order.couponCode == null || order.discountAmount == null || order.discountAmount!! <= BigDecimal.ZERO) {
            return ""
        }

        val couponCode = order.couponCode!!
        val discountAmount = order.discountAmount!!
        val discountFormatted = "R$ %.2f".format(discountAmount.toDouble())

        return """
            <!-- CUPOM APLICADO -->
            <div style="background:#f8f9fa;border:1px solid #dee2e6;border-radius:8px;padding:16px;margin:16px 0;text-align:center;">
              <div style="color:#28a745;font-size:24px;margin-bottom:8px;">🎯</div>
              <div style="font-weight:700;color:#495057;font-size:16px;margin-bottom:4px;">CUPOM APLICADO</div>
              <div style="font-weight:600;color:#6c757d;font-size:14px;margin-bottom:8px;">Código: ${escapeHtml(couponCode)}</div>
              <div style="font-weight:700;color:#28a745;font-size:18px;">Você economizou $discountFormatted! 💰</div>
            </div>
        """.trimIndent()
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
