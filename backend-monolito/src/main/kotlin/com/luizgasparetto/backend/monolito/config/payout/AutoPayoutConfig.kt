package com.luizgasparetto.backend.monolito.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.math.BigDecimal

@Configuration
@ConfigurationProperties(prefix = "efi")
class AutoPayoutConfig {

    @NestedConfigurationProperty
    var pix: Pix = Pix()

    @NestedConfigurationProperty
    var payout: Payout = Payout()

    class Pix {
        var clientId: String = ""
        var clientSecret: String = ""
        var chave: String = ""          // efi.pix.chave
        var certPath: String = ""       // efi.pix.cert-path
        var certPassword: String = ""   // efi.pix.cert-password
        var sandbox: Boolean = false
    }

    // AutoPayoutConfig.kt
    class Payout {
        var favoredKey: String? = null   // só isso aqui
    }
}
