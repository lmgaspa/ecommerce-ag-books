package com.luizgasparetto.backend.monolito.config.efi

import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.client5.http.socket.ConnectionSocketFactory
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.core5.http.config.RegistryBuilder
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.apache.hc.core5.util.Timeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.io.ClassPathResource
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.util.ResourceUtils
import org.springframework.web.client.RestTemplate
import java.io.FileInputStream
import java.security.KeyStore

@Configuration
class EfiRestTemplateConfig(
    @Value("\${efi.pix.cert-path:}") private val certPath: String,
    @Value("\${efi.pix.cert-password:}") private val certPass: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean("efiRestTemplate")
    @Primary
    fun efiRestTemplate(): RestTemplate {
        // Em PROD, NÃO deixe vazio: a Efí exige mTLS também no /oauth/token.
        if (certPath.isBlank()) {
            log.warn("efi.pix.cert-path vazio — RestTemplate SEM mTLS (apenas DEV).")
            return RestTemplate()
        }

        // PKCS12 (classpath:... ou caminho absoluto)
        val ks = KeyStore.getInstance("PKCS12")
        val pwd = certPass.toCharArray()
        val input = if (certPath.startsWith("classpath:"))
            ClassPathResource(certPath.removePrefix("classpath:")).inputStream
        else
            FileInputStream(ResourceUtils.getFile(certPath))
        input.use { ks.load(it, pwd) }

        // SSLContext com chave de cliente (mTLS)
        val sslContext = SSLContextBuilder.create()
            .loadKeyMaterial(ks, pwd)
            .build()
        val sslSF = SSLConnectionSocketFactory(sslContext)

        // Pool + registry http/https
        val registry = RegistryBuilder.create<ConnectionSocketFactory>()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", sslSF)
            .build()

        val cm = PoolingHttpClientConnectionManager(registry).apply {
            defaultMaxPerRoute = 50
            maxTotal = 200
        }

        // Timeouts no HttpClient (funciona em Boot 2.x/3.x)
        val reqCfg = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))
            .setResponseTimeout(Timeout.ofSeconds(20))
            .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(reqCfg)
            .evictExpiredConnections()
            .evictIdleConnections(Timeout.ofSeconds(30))
            .build()

        val factory = HttpComponentsClientHttpRequestFactory(httpClient)
        log.info(
            "EFI mTLS RestTemplate pronto (certPath={}, classpath={})",
            certPath, certPath.startsWith("classpath:")
        )
        return RestTemplate(factory)
    }
}
