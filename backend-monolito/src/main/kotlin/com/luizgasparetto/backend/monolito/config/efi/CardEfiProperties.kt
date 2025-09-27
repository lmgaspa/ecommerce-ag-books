package com.luizgasparetto.backend.monolito.config.efi

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("efi.card")
data class CardEfiProperties(
    var clientId: String = "",
    var clientSecret: String = "",
    var sandbox: Boolean = true
)