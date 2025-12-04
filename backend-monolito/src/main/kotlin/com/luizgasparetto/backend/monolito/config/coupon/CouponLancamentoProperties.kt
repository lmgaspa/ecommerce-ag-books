package com.luizgasparetto.backend.monolito.config.coupon

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties("coupon.lancamento")
data class CouponLancamentoProperties(
    val enabled: Boolean = true,
    // Valores padrão apenas em application.yml (dev local)
    // Em produção (Heroku) devem vir via env vars
    val code: String? = null,
    val name: String? = null,
    val description: String? = null,
    val discountValue: BigDecimal? = null,
    val minimumOrderValue: BigDecimal = BigDecimal.ZERO,
    val validFrom: String? = null, // ISO format, null = NOW
    val validUntil: String? = null, // ISO format, null = sem expiração
    val active: Boolean = true
)

