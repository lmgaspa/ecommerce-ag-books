package com.luizgasparetto.backend.monolito

import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import com.luizgasparetto.backend.monolito.config.efi.PixEfiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableConfigurationProperties(PixEfiProperties::class, CardEfiProperties::class)
class BackendMonolitoApplication

fun main(args: Array<String>) {
	runApplication<BackendMonolitoApplication>(*args)
}
