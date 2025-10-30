package com.luizgasparetto.backend.monolito.privacy

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.privacy")
data class PrivacyProps(
    var cookieSignSecret: String = "",
    var ipSalt: String = "",
)
