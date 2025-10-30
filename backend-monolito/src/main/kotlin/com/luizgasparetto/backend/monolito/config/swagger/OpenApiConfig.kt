package com.luizgasparetto.backend.monolito.config.swagger

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Agenor Gasparetto – Ecommerce API")
                .version("v1")
                .description("Endpoints do checkout Pix, cartão de crédito, webhook e catálogo")
        )
}