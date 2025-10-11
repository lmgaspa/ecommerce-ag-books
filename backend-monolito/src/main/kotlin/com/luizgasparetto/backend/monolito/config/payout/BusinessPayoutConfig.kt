package com.luizgasparetto.backend.monolito.config.payout

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "efi.payout")
class BusinessPayoutConfig {
    var feePercent: BigDecimal = BigDecimal.ZERO
    var feeFixed: BigDecimal = BigDecimal.ZERO
    var marginPercent: BigDecimal = BigDecimal.ZERO
    var marginFixed: BigDecimal = BigDecimal.ZERO
    var minSend: BigDecimal = BigDecimal("1.00")
}
