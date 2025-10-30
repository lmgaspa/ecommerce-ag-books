// src/main/kotlin/com/luizgasparetto/backend/monolito/config/PropsConfig.kt
package com.luizgasparetto.backend.monolito.config.payments

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(EfiPayoutProps::class)
class PropsConfig
