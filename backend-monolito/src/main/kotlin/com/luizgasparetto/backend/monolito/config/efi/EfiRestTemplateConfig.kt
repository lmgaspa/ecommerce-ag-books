// src/main/kotlin/com/luizgasparetto/backend/monolito/config/efi/EfiRestTemplateConfig.kt
package com.luizgasparetto.backend.monolito.config.efi

import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.client5.http.socket.ConnectionSocketFactory
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.core5.http.config.RegistryBuilder
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate
import java.security.KeyStore

@Configuration
class EfiRestTemplateConfig(
    @Value("\${efi.pix.cert-path:}") private val certPath: String,
    @Value("\${efi.pix.cert-password:}") private val certPass: String
) {
    @Bean("efiRestTemplate")
    fun efiRestTemplate(): RestTemplate {
        if (certPath.isBlank()) return RestTemplate() // fallback p/ CHARGES/dev
        val ks = KeyStore.getInstance("PKCS12").apply {
            ResourceUtils.getURL(certPath).openStream().use { load(it, certPass.toCharArray()) }
        }
        val ssl = SSLContextBuilder.create().loadKeyMaterial(ks, certPass.toCharArray()).build()
        val sf = SSLConnectionSocketFactory(ssl)
        val cm = PoolingHttpClientConnectionManager(
            RegistryBuilder.create<ConnectionSocketFactory>().register("https", sf).build()
        )
        val httpClient = HttpClients.custom().setConnectionManager(cm).build()
        return RestTemplate(HttpComponentsClientHttpRequestFactory(httpClient))
    }
}
