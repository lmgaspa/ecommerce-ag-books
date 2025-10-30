// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/PixPayoutProvider.kt
package com.luizgasparetto.backend.monolito.payments.web

import java.math.BigDecimal

interface PixPayoutProvider {
    fun sendPixPayout(orderId: Long, amount: BigDecimal, favoredPixKey: String): String
}
