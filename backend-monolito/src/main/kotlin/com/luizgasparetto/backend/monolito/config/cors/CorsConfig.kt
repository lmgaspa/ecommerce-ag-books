package com.luizgasparetto.backend.monolito.config.cors

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {

        override fun addCorsMappings(registry: CorsRegistry) {
            // SSE: só GET neste prefixo
            registry.addMapping("/api/orders/**")
                .allowedOriginPatterns( // aceita variações de subdomínio
                    "https://www.agenorgasparetto.com.br",
                    "https://agenorgasparetto.com.br",
                    "http://localhost:5173"
                )
                .allowedMethods("GET")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600)

            // (Opcional) REST comum
            registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "https://www.agenorgasparetto.com.br",
                    "https://agenorgasparetto.com.br",
                    "http://localhost:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600)
        }
    }
}