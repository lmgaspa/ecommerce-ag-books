// src/main/kotlin/com/luizgasparetto/backend/monolito/config/efi/EfiRestTemplateConfig.kt
package com.luizgasparetto.backend.monolito.config.efi

import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.io.HttpClientConnectionManager
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy
import org.apache.hc.client5.http.ssl.TlsSocketStrategy
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
        // Sem certificado → usa RestTemplate "normal" (ex: CHARGES/dev)
        if (certPath.isBlank()) {
            return RestTemplate()
        }

        // Carrega o .p12/.pfx no KeyStore
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            ResourceUtils.getURL(certPath).openStream().use { input ->
                load(input, certPass.toCharArray())
            }
        }

        // Cria SSLContext com o certificado cliente (mTLS)
        val sslContext = SSLContextBuilder.create()
            .loadKeyMaterial(keyStore, certPass.toCharArray())
            .build()

        // Estratégia TLS moderna (substitui SSLConnectionSocketFactory)
        val tlsStrategy: TlsSocketStrategy = DefaultClientTlsStrategy(sslContext)

        // Connection manager com pooling, usando a TLS strategy
        val connectionManager: HttpClientConnectionManager =
            PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tlsStrategy)
                .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build()

        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient).apply {
            // Se quiser, aqui dá pra configurar timeouts:
            // setConnectTimeout(10_000)
            // setReadTimeout(30_000)
        }

        return RestTemplate(requestFactory)
    }
}
