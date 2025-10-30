// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/StubPixPayoutProvider.kt
package com.luizgasparetto.backend.monolito.payments.web

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Profile("stub")
@Component
class StubPixPayoutProvider : PixPayoutProvider {
    override fun sendPixPayout(orderId: Long, amount: BigDecimal, favoredPixKey: String): String {
        return "STUB-" + UUID.randomUUID().toString().take(8)
    }
}
