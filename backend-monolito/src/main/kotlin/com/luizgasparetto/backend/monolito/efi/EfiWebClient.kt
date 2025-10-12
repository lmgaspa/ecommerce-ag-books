// src/main/kotlin/.../efi/EfiWebClient.kt
package com.luizgasparetto.backend.monolito.efi

import io.netty.handler.ssl.SslContextBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

@Configuration
class EfiWebClient(
    @Value("\${efi.pix.cert-path}") private val p12: Resource,
    @Value("\${efi.pix.cert-password:}") private val p12Pass: String,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean
) {
    @Bean
    fun efiPixWebClient(): WebClient {
        val ks = KeyStore.getInstance("PKCS12").apply {
            p12.inputStream.use { load(it, p12Pass.toCharArray()) }
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(ks, p12Pass.toCharArray())
        }
        val sslCtx = SslContextBuilder.forClient().keyManager(kmf).build()
        val http = HttpClient.create().secure { it.sslContext(sslCtx) }
        val base = if (sandbox) "https://api-pix-h.efipay.com.br" else "https://pix.api.efipay.com.br"

        return WebClient.builder()
            .baseUrl(base)
            .clientConnector(ReactorClientHttpConnector(http))
            .build()
    }
}
