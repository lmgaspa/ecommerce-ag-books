package com.luizgasparetto.backend.monolito.payment.services

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PaymentCalculator {
    fun netFor(orderTotal: BigDecimal, grossForAuthor: BigDecimal, takeFees: Boolean)
            = grossForAuthor.setScale(2, RoundingMode.HALF_UP)
}