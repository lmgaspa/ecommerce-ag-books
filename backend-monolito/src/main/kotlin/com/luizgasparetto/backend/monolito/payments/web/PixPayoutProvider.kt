// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/PixPayoutProvider.kt
package com.luizgasparetto.backend.monolito.payments.web

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

interface PixPayoutProvider {
    fun sendPixPayout(orderId: Long, amount: BigDecimal, pixKey: String): String
}

/** Stub: apenas loga e retorna um providerRef fake. Troque pela integração Efí depois. */
@Component
@Primary
class EfiPixPayoutStub : PixPayoutProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun sendPixPayout(orderId: Long, amount: BigDecimal, pixKey: String): String {
        log.info("Enviando PIX payout (STUB) orderId={} amount={} pixKey={}", orderId, amount, pixKey)
        return "STUB-${UUID.randomUUID()}"
    }
}
