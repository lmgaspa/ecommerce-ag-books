package com.luizgasparetto.backend.monolito.config.efi

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("efi.pix")
data class PixEfiProperties(
    var clientId: String = "",
    var clientSecret: String = "",
    var chave: String = "",
    var sandbox: Boolean = true,
    var certPath: String = "",
    var certPassword: String = ""
)