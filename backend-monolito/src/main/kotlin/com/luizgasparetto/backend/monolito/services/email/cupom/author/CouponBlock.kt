package com.luizgasparetto.backend.monolito.services.email.cupom.author

import java.math.BigDecimal

/**
 * Helper para gerar bloco HTML de cupom para emails do AUTOR
 */
object CouponBlock {

    fun build(couponCode: String, discountAmount: BigDecimal): String {
        val discountFormatted = "R$ %.2f".format(discountAmount.toDouble())
        val escapedCode = escapeHtml(couponCode)

        return """
            <!-- CUPOM UTILIZADO - AUTOR -->
            <div style="background:#fff3cd;border:1px solid #ffeaa7;border-radius:8px;padding:16px;margin:16px 0;text-align:center;">
              <div style="color:#856404;font-size:24px;margin-bottom:8px;">🎫</div>
              <div style="font-weight:700;color:#856404;font-size:16px;margin-bottom:4px;">CUPOM UTILIZADO</div>
              <div style="font-weight:600;color:#856404;font-size:14px;margin-bottom:8px;">Código: $escapedCode</div>
              <div style="font-weight:700;color:#856404;font-size:18px;">Pagamento reduzido em $discountFormatted</div>
            </div>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}

