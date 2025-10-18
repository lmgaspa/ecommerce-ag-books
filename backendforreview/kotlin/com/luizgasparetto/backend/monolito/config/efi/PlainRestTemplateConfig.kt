// src/main/kotlin/.../config/efi/PlainRestTemplateConfig.kt
package com.luizgasparetto.backend.monolito.config.efi

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class PlainRestTemplateConfig {
    @Bean("plainRestTemplate")
    fun plainRestTemplate(): RestTemplate = RestTemplate()
}
