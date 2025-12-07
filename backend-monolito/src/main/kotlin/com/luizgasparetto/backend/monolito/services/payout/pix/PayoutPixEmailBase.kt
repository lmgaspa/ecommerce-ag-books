package com.luizgasparetto.backend.monolito.services.payout.pix

import com.luizgasparetto.backend.monolito.config.payments.EfiPixPayoutProps
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.email.common.EmailFooter
import com.luizgasparetto.backend.monolito.services.email.payout.DiscountDetailsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import java.math.BigDecimal
import java.nio.charset.StandardCharsets

/**
 * Classe base com funcionalidades compartilhadas para emails de Payout PIX
 */
abstract class PayoutPixEmailBase(
    protected val mailSender: JavaMailSender,
    protected val orderRepository: OrderRepository,
    protected val jdbc: NamedParameterJdbcTemplate,
    protected val payoutProps: EfiPixPayoutProps,
    @Value("\${email.author}") protected val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") protected val brandName: String,
    @Value("\${mail.from:}") protected val configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") protected val logoUrl: String,
    @Value("\${application.timezone:America/Bahia}") protected val appTz: String,
    @Value("\${efi.payout.favored-key:}") protected val favoredKeyFromConfig: String,
    @Value("\${efi.pix.sandbox:false}") protected val sandbox: Boolean,
    @Value("\${mail.host:}") protected val mailHost: String
) {
    protected val log = LoggerFactory.getLogger(javaClass)

    /**
     * Resolve o endereço FROM sem depender de spring.mail.*:
     * 1) MAIL_USERNAME (env)
     * 2) mail.from (configuredFrom)
     * 3) email do autor (authorEmail)
     */
    protected fun resolveFrom(): String {
        val fromEnv = System.getenv("MAIL_USERNAME")?.takeIf { it.isNotBlank() }
        val fromConfig = configuredFrom.takeIf { it.isNotBlank() }
        val resolved = fromEnv ?: fromConfig ?: authorEmail

        log.debug(
            "📧 PayoutPixEmailBase.resolveFrom(): resolvedFrom='{}' (env='{}', config='{}', author='{}')",
            resolved, fromEnv, fromConfig, authorEmail
        )

        return resolved
    }

    /**
     * Envia email usando MimeMessageHelper
     */
    protected fun sendInternal(
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

    /**
     * Calcula o valor líquido (após taxas) usando as mesmas regras do PaymentTriggerService
     */
    protected fun calculateNetAmount(amountGross: BigDecimal): BigDecimal {
        return DiscountDetailsHelper.calculateDiscountDetails(amountGross, payoutProps).amountNet
    }

    protected fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    protected fun onlyDigits(s: String): String = s.filter { it.isDigit() }

    protected fun formatCpfIfPossible(key: String?): String? {
        val d = onlyDigits(key.orEmpty())
        return if (d.length == 11) {
            "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
        } else {
            null
        }
    }

    /**
     * Busca payout_id por order_id
     */
    protected fun findPayoutIdByOrderId(orderId: Long): Long? {
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


    /**
     * Gera o bloco de cupom utilizado no pedido
     */
    protected fun buildCouponBlock(orderId: Long): String {
        return try {
            val order = orderRepository.findById(orderId).orElse(null)
            if (order?.couponCode != null &&
                order.discountAmount != null &&
                order.discountAmount!! > BigDecimal.ZERO
            ) {
                com.luizgasparetto.backend.monolito.services.email.cupom.author.CouponBlock.build(
                    order.couponCode!!,
                    order.discountAmount!!
                )
            } else {
                ""
            }
        } catch (e: Exception) {
            log.warn("PayoutEmail [PIX]: erro ao buscar informações do cupom para pedido {}: {}", orderId, e.message)
            ""
        }
    }

    /**
     * Gera o rodapé padrão do email
     */
    protected fun buildFooter(): String = EmailFooter.build()

    /**
     * Gera o header padrão do email
     */
    protected fun buildHeader(subtitle: String): String {
        return """
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
        """.trimIndent()
    }

    /**
     * Gera o bloco de contato padrão
     */
    protected fun buildContactBlock(): String {
        return """
            <p style="margin:16px 0 0;color:#555">
                Dúvidas? Fale com a <strong>${escape(brandName)}</strong><br>
                ✉️ <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> · 
                💬 <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
            </p>
        """.trimIndent()
    }
}

