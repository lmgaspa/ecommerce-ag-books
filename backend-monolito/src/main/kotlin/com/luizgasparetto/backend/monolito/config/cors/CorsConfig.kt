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
            // 1) SSE
            registry.addMapping("/api/v1/orders/**")
                .allowedOriginPatterns(
                    "https://www.agenorgasparetto.com.br",
                    "https://agenorgasparetto.com.br",
                    "http://localhost:5173"
                )
                .allowedMethods("GET")
                .allowedHeaders("Accept", "Cache-Control", "Content-Type", "Last-Event-ID")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600)

            // 2) Privacy (expor Set-Cookie, sem credentials)
            registry.addMapping("/api/v1/privacy/**")
                .allowedOriginPatterns(
                    "https://www.agenorgasparetto.com.br",
                    "https://agenorgasparetto.com.br",
                    "http://localhost:5173"
                )
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type")
                .exposedHeaders("Set-Cookie", "Content-Type")
                .allowCredentials(false)
                .maxAge(3600)

            // 3) REST genérico
            registry.addMapping("/api/v1/**")
                .allowedOriginPatterns(
                    "https://www.agenorgasparetto.com.br",
                    "https://agenorgasparetto.com.br",
                    "http://localhost:5173"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Accept", "Content-Type", "Authorization")
                .exposedHeaders("Content-Type")
                .allowCredentials(false)
                .maxAge(3600)
        }
    }
}
