package com.luizgasparetto.backend.monolito.services.payout.card

import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailStatus
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.PayoutEmailRepository
import com.luizgasparetto.backend.monolito.services.email.common.EmailFooter
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.nio.charset.StandardCharsets

/**
 * Responsável por enviar emails de falha de repasse CARD
 */
@Component
class PayoutCardFailedEmailSender(
    mailSender: JavaMailSender,
    orderRepository: OrderRepository,
    payoutEmailRepository: PayoutEmailRepository,
    jdbc: NamedParameterJdbcTemplate,
    payoutProps: EfiPayoutProps,
    @Value("\${email.author}") authorEmail: String,
    @Value("\${application.brand.name:Agenor Gasparetto - E-Commerce}") brandName: String,
    @Value("\${mail.from:}") configuredFrom: String,
    @Value("\${mail.logo.url:https://www.andescoresoftware.com.br/AndesCore.jpg}") logoUrl: String,
    @Value("\${application.timezone:America/Bahia}") appTz: String,
    @Value("\${efi.payout.favored-key:}") favoredKeyFromConfig: String,
    @Value("\${efi.card.sandbox:false}") sandbox: Boolean,
    @Value("\${mail.host:}") mailHost: String
) : PayoutCardEmailBase(
    mailSender, orderRepository, payoutEmailRepository, jdbc, payoutProps,
    authorEmail, brandName, configuredFrom, logoUrl, appTz, favoredKeyFromConfig, sandbox, mailHost
) {

    fun send(
        orderId: Long,
        amount: BigDecimal,
        payeePixKey: String?,
        idEnvio: String,
        errorCode: String,
        errorMessage: String,
        to: String = authorEmail
    ) {
        val from = resolveFrom()

        log.info(
            "📧 PAYOUT CARD EMAIL [FAILED]: iniciando envio - orderId={}, to={}, sandbox={}, mailHost={}, errorCode={}",
            orderId, to, sandbox, mailHost, errorCode
        )
        log.debug(
            "📧 PAYOUT CARD EMAIL [FAILED]: config -> from={}, authorEmail={}, appTz={}, favoredKeyFromConfig={}",
            from, authorEmail, appTz, favoredKeyFromConfig
        )

        val maskedKey = payeePixKey?.let { mask(it) } ?: "N/A"
        val amountNet = calculateNetAmount(amount)
        val amountFormatted = String.format("R$ %.2f", amountNet)

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: #dc3545; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 8px 8px; }
                    .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #dc3545; }
                    .amount { font-size: 24px; font-weight: bold; color: #dc3545; }
                    .error { background: #f8d7da; color: #721c24; padding: 15px; border-radius: 4px; margin: 10px 0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>❌ Falha no Repasse Cartão</h1>
                        <p>Não foi possível processar o repasse</p>
                    </div>
                    <div class="content">
                        <div class="info-box">
                            <h3>📋 Detalhes do Repasse</h3>
                            <p><strong>Pedido:</strong> #$orderId</p>
                            <p><strong>Valor:</strong> <span class="amount">$amountFormatted</span></p>
                            <p><strong>Chave PIX:</strong> $maskedKey</p>
                            <p><strong>ID Envio:</strong> $idEnvio</p>
                        </div>
                        
                        <div class="info-box">
                            <h3>❌ Erro</h3>
                            <div class="error">
                                <p><strong>Código:</strong> ${escape(errorCode)}</p>
                                <p><strong>Mensagem:</strong> ${escape(errorMessage)}</p>
                            </div>
                            <p>Por favor, verifique os dados e tente novamente ou entre em contato com o suporte.</p>
                        </div>
                    </div>
                    ${EmailFooter.build()}
                </div>
            </body>
            </html>
        """.trimIndent()

        val success = try {
            val message = mailSender.createMimeMessage()
            val helper = org.springframework.mail.javamail.MimeMessageHelper(message, true, StandardCharsets.UTF_8.name())

            log.debug(
                "📧 PAYOUT CARD EMAIL [FAILED]: preparando MimeMessage - orderId={}, from={}, to={}, subject={}",
                orderId, from, to, "❌ Falha no Repasse Cartão - Pedido #$orderId"
            )

            helper.setFrom(from, brandName)
            helper.setTo(to)
            helper.setSubject("❌ Falha no Repasse Cartão - Pedido #$orderId")
            helper.setText(htmlContent, true)

            log.info("📧 PAYOUT CARD EMAIL [FAILED]: tentando enviar - orderId={}, sandbox={}", orderId, sandbox)
            mailSender.send(message)

            log.info(
                "✅ PAYOUT CARD EMAIL [FAILED]: notificação enviada -> {} (orderId={}, amount={}, errorCode={}, sandbox={})",
                to, orderId, amountFormatted, errorCode, sandbox
            )
            true
        } catch (e: Exception) {
            log.error(
                "❌ PAYOUT CARD EMAIL [FAILED]: erro ao enviar email de falha (orderId={}, amount={}, sandbox={}, mailHost={}): {}",
                orderId, amount, sandbox, mailHost, e.message, e
            )
            false
        }

        log.debug(
            "📧 PAYOUT CARD EMAIL [FAILED]: resultado do envio - orderId={}, success={}, sandbox={}",
            orderId, success, sandbox
        )

        val finalStatus = if (success) PayoutEmailStatus.SENT else PayoutEmailStatus.FAILED
        val finalErrorMessage = if (!success) {
            "Erro ao enviar e-mail de falha: $errorMessage"
        } else {
            errorMessage
        }

        persistEmail(
            orderId = orderId,
            to = to,
            emailType = PayoutEmailType.REPASSE_CARD,
            status = finalStatus,
            errorMessage = finalErrorMessage
        )
    }
}

