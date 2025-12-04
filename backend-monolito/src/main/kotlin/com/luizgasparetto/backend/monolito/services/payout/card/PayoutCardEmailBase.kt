package com.luizgasparetto.backend.monolito.services.payout.card

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmail
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailStatus
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.PayoutEmailRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.Year

/**
 * Classe base com funcionalidades compartilhadas para emails de Payout Card
 */
abstract class PayoutCardEmailBase(
    protected val mailSender: JavaMailSender,
    protected val orderRepository: OrderRepository,
    protected val payoutEmailRepository: PayoutEmailRepository,
    protected val jdbc: NamedParameterJdbcTemplate,
    protected val payoutProps: EfiPayoutProps,
    @Value("\${email.author}") val authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") protected val brandName: String,
    @Value("\${mail.from:}") protected val configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") protected val logoUrl: String,
    @Value("\${application.timezone:America/Bahia}") protected val appTz: String,
    @Value("\${efi.payout.favored-key:}") protected val favoredKeyFromConfig: String,
    @Value("\${efi.card.sandbox:false}") protected val sandbox: Boolean,
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
            "📧 PayoutCardEmailBase.resolveFrom(): resolvedFrom='{}' (env='{}', config='{}', author='{}')",
            resolved, fromEnv, fromConfig, authorEmail
        )

        return resolved
    }

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
            "📧 PAYOUT CARD EMAIL [{}]: configurando MimeMessage - orderId={}, from={}, to={}, subject={}, sandbox={}, mailHost={}",
            context, orderId, from, to, subject, sandbox, mailHost
        )

        helper.setFrom(from, brandName)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(html, true)

        return try {
            log.info("📧 PAYOUT CARD EMAIL [{}]: tentando enviar - orderId={}, sandbox={}", context, orderId, sandbox)
            mailSender.send(msg)
            log.info(
                "✅ PAYOUT CARD EMAIL [{}]: enviado com sucesso -> {} (orderId={}, sandbox={})",
                context, to, orderId, sandbox
            )
            true
        } catch (e: Exception) {
            log.error(
                "❌ PAYOUT CARD EMAIL [{}]: erro ao enviar para {} (orderId={}, sandbox={}, mailHost={}): {}",
                context, to, orderId, sandbox, mailHost, e.message, e
            )
            false
        }
    }

    protected fun findPayoutIdByOrderId(orderId: Long): Long? {
        return try {
            val row = jdbc.queryForMap(
                "SELECT id FROM payment_payouts WHERE order_id = :orderId LIMIT 1",
                mapOf("orderId" to orderId)
            )
            val id = (row["id"] as? Number)?.toLong()
            log.debug("PayoutEmail [CARD]: payout encontrado para orderId={} -> payoutId={}", orderId, id)
            id
        } catch (e: Exception) {
            log.warn("PayoutEmail [CARD]: payout não encontrado para orderId={}: {}", orderId, e.message)
            null
        }
    }

    protected fun persistEmail(
        orderId: Long,
        to: String,
        emailType: PayoutEmailType,
        status: PayoutEmailStatus,
        errorMessage: String? = null
    ) {
        try {
            val payoutId = findPayoutIdByOrderId(orderId)

            val payoutEmail = PayoutEmail(
                payoutId = payoutId,
                orderId = orderId,
                toEmail = to,
                emailType = emailType.name,
                sentAt = OffsetDateTime.now(),
                status = status,
                errorMessage = errorMessage
            )

            payoutEmailRepository.save(payoutEmail)

            if (payoutId != null) {
                log.debug(
                    "PayoutEmail [CARD]: persistido (com payoutId) orderId={} payoutId={} type={} status={} error={}",
                    orderId, payoutId, emailType, status, errorMessage
                )
            } else {
                log.debug(
                    "PayoutEmail [CARD]: persistido (sem payoutId) orderId={} type={} status={} error={}",
                    orderId, emailType, status, errorMessage
                )
            }
        } catch (e: Exception) {
            log.error("PayoutEmail [CARD]: erro ao persistir e-mail para orderId={}: {}", orderId, e.message, e)
        }
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

    protected fun mask(pixKey: String): String {
        return if (pixKey.length <= 6) {
            "***"
        } else {
            pixKey.take(3) + "***" + pixKey.takeLast(3)
        }
    }

    /**
     * Data class para detalhamento do desconto aplicado no repasse
     */
    protected data class DiscountDetails(
        val fee: BigDecimal,
        val margin: BigDecimal,
        val totalDiscount: BigDecimal,
        val amountGross: BigDecimal,
        val amountNet: BigDecimal
    )

    /**
     * Calcula o valor líquido (após taxas) usando as mesmas regras do PaymentTriggerService
     */
    protected fun calculateNetAmount(amountGross: BigDecimal): BigDecimal {
        return calculateDiscountDetails(amountGross).amountNet
    }

    /**
     * Calcula o detalhamento completo do desconto (fee, margin, total)
     */
    protected fun calculateDiscountDetails(amountGross: BigDecimal): DiscountDetails {
        val hundred = BigDecimal("100")
        val feePercent = BigDecimal.valueOf(payoutProps.feePercent)
        val feeFixed = BigDecimal.valueOf(payoutProps.feeFixed)
        val marginPercent = BigDecimal.valueOf(payoutProps.marginPercent)
        val marginFixed = BigDecimal.valueOf(payoutProps.marginFixed)
        val includeGatewayFees = payoutProps.fees.includeGatewayFees

        val fee = if (includeGatewayFees) {
            amountGross.multiply(feePercent).divide(hundred, 2, RoundingMode.HALF_UP).plus(feeFixed)
        } else {
            BigDecimal.ZERO
        }
        val margin = amountGross.multiply(marginPercent).divide(hundred, 2, RoundingMode.HALF_UP).plus(marginFixed)

        val totalDiscount = fee.plus(margin).setScale(2, RoundingMode.HALF_UP)
        var net = amountGross.minus(totalDiscount).setScale(2, RoundingMode.HALF_UP)
        
        if (net >= amountGross) {
            net = amountGross.minus(BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP)
        }
        
        return DiscountDetails(
            fee = fee.setScale(2, RoundingMode.HALF_UP),
            margin = margin.setScale(2, RoundingMode.HALF_UP),
            totalDiscount = totalDiscount,
            amountGross = amountGross.setScale(2, RoundingMode.HALF_UP),
            amountNet = net
        )
    }

    /**
     * Gera o bloco HTML com detalhamento do desconto aplicado no repasse
     */
    protected fun buildDiscountDetailsBlock(details: DiscountDetails): String {
        val grossFmt = "R$ %.2f".format(details.amountGross.toDouble())
        val feeFmt = "R$ %.2f".format(details.fee.toDouble())
        val marginFmt = "R$ %.2f".format(details.margin.toDouble())
        val discountFmt = "R$ %.2f".format(details.totalDiscount.toDouble())
        val netFmt = "R$ %.2f".format(details.amountNet.toDouble())
        
        val feePercentFmt = if (payoutProps.fees.includeGatewayFees) {
            "%.2f%%".format(payoutProps.feePercent)
        } else {
            "-"
        }
        val marginPercentFmt = "%.2f%%".format(payoutProps.marginPercent)
        
        // Formata a descrição da taxa do gateway
        val feeLabel = if (payoutProps.fees.includeGatewayFees) {
            if (payoutProps.feeFixed > 0) {
                "Taxa do gateway (${feePercentFmt} + R$ ${payoutProps.feeFixed}):"
            } else {
                "Taxa do gateway (${feePercentFmt}):"
            }
        } else {
            "Taxa do gateway: (não aplicada)"
        }
        
        // Formata a descrição da margem
        val marginLabel = if (payoutProps.marginFixed > 0) {
            "Margem (${marginPercentFmt} + R$ ${payoutProps.marginFixed}):"
        } else {
            "Margem (${marginPercentFmt}):"
        }
        
        return """
            <!-- DETALHAMENTO DE DESCONTO -->
            <div style="background:#f8f9fa;border:1px solid #dee2e6;border-radius:8px;padding:20px;margin:16px 0;">
              <div style="font-weight:700;color:#495057;font-size:16px;margin-bottom:12px;text-align:center;">
                💰 Detalhamento do Repasse
              </div>
              <table width="100%" cellspacing="0" cellpadding="8" style="border-collapse:collapse;font-size:14px;">
                <tr>
                  <td style="color:#6c757d;padding:6px 8px;text-align:left;">Valor bruto do pedido:</td>
                  <td style="font-weight:600;color:#212529;padding:6px 8px;text-align:right;">$grossFmt</td>
                </tr>
                <tr style="background:#fff;">
                  <td style="color:#6c757d;padding:6px 8px;text-align:left;border-top:1px solid #dee2e6;">
                    $feeLabel
                  </td>
                  <td style="color:#dc3545;font-weight:600;padding:6px 8px;text-align:right;border-top:1px solid #dee2e6;">-$feeFmt</td>
                </tr>
                <tr>
                  <td style="color:#6c757d;padding:6px 8px;text-align:left;border-top:1px solid #dee2e6;">
                    $marginLabel
                  </td>
                  <td style="color:#dc3545;font-weight:600;padding:6px 8px;text-align:right;border-top:1px solid #dee2e6;">-$marginFmt</td>
                </tr>
                <tr style="background:#fff3cd;">
                  <td style="font-weight:700;color:#856404;padding:8px;text-align:left;border-top:2px solid #ffc107;">
                    Custos de Operação:
                  </td>
                  <td style="font-weight:700;color:#dc3545;padding:8px;text-align:right;border-top:2px solid #ffc107;">-$discountFmt</td>
                </tr>
                <tr style="background:#d4edda;">
                  <td style="font-weight:700;color:#155724;padding:10px 8px;text-align:left;border-top:2px solid #28a745;font-size:16px;">
                    Valor líquido repassado:
                  </td>
                  <td style="font-weight:700;color:#155724;padding:10px 8px;text-align:right;border-top:2px solid #28a745;font-size:18px;">
                    $netFmt
                  </td>
                </tr>
              </table>
            </div>
        """.trimIndent()
    }

    protected fun buildCouponBlock(orderId: Long): String {
        return try {
            val order = orderRepository.findById(orderId).orElse(null)
            if (order?.couponCode != null &&
                order.discountAmount != null &&
                order.discountAmount!! > BigDecimal.ZERO
            ) {
                val couponCode = order.couponCode!!
                val discountAmount = order.discountAmount!!
                val discountFormatted = "R$ %.2f".format(discountAmount.toDouble())
                """
                <!-- CUPOM UTILIZADO NO PEDIDO -->
                <div style="background:#fff3cd;border:1px solid #ffeaa7;border-radius:8px;padding:16px;margin:16px 0;text-align:center;">
                  <div style="color:#856404;font-size:24px;margin-bottom:8px;">🎫</div>
                  <div style="font-weight:700;color:#856404;font-size:16px;margin-bottom:4px;">CUPOM UTILIZADO</div>
                  <div style="font-weight:600;color:#856404;font-size:14px;margin-bottom:8px;">Código: ${escape(couponCode)}</div>
                  <div style="font-weight:700;color:#856404;font-size:18px;">Pagamento reduzido em $discountFormatted</div>
                </div>
                """.trimIndent()
            } else {
                ""
            }
        } catch (e: Exception) {
            log.warn("PayoutEmail [CARD]: erro ao buscar informações do cupom para pedido {}: {}", orderId, e.message)
            ""
        }
    }
}

