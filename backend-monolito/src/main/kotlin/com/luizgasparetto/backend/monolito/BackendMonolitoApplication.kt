package com.luizgasparetto.backend.monolito

import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import com.luizgasparetto.backend.monolito.config.efi.PixEfiProperties
import com.luizgasparetto.backend.monolito.config.payments.EfiPayoutProps
import com.luizgasparetto.backend.monolito.privacy.PrivacyProps
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableConfigurationProperties(PrivacyProps::class, PixEfiProperties::class, CardEfiProperties::class, EfiPayoutProps::class)
class BackendMonolitoApplication

fun main(args: Array<String>) {
	runApplication<BackendMonolitoApplication>(*args)
}
