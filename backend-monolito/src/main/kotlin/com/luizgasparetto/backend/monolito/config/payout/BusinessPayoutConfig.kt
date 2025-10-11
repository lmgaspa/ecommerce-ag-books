package com.luizgasparetto.backend.monolito.config.payout

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "efi.payout")
class BusinessPayoutConfig {
    /** chave PIX destino padrão (pode ser sobrescrita por parâmetro no service) */
    var favoredKey: String? = null

    /** tarifas Efí */
    var feePercent: BigDecimal = BigDecimal.ZERO   // ex.: 1.59 (%)
    var feeFixed: BigDecimal   = BigDecimal.ZERO   // ex.: 0.49 (R$)

    /** sua margem “a menos” no repasse */
    var marginPercent: BigDecimal = BigDecimal("0.0") // ex.: 0.8 (%)
    var marginFixed: BigDecimal   = BigDecimal.ZERO    // ex.: 0.20 (R$)

    /** valor mínimo para repasse (em BRL) */
    var minSend: BigDecimal = BigDecimal("1.00")
}