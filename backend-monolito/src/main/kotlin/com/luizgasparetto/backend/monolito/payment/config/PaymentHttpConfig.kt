package com.luizgasparetto.backend.monolito.payment.config

import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.client5.http.ssl.TrustAllStrategy
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.security.KeyStore
import javax.net.ssl.SSLContext

@Configuration
class PaymentHttpConfig(
    @Value("\${efi.pix.cert-path}") private val certPath: Resource,
    @Value("\${efi.pix.cert-password:}") private val certPassword: String
) {

    @Bean
    fun paymentRestTemplate(): RestTemplate {
        val sslContext: SSLContext = buildSslContext()

        // ✅ Defina timeouts de CONEXÃO e SOCKET no ConnectionConfig (novo)
        val connCfg: ConnectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(30))   // tempo para estabelecer conexão (inclui handshake TLS)
            .setSocketTimeout(Timeout.ofSeconds(60))    // inatividade do socket (read)
            .build()

        // ✅ Timeouts por requisição (ex.: resposta total)
        val reqCfg: RequestConfig = RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(60))  // tempo máximo aguardando a resposta completa
            .build()

        val cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(SSLConnectionSocketFactory(sslContext))
            .setDefaultConnectionConfig(connCfg)        // << usa o ConnectionConfig aqui
            .setMaxConnTotal(100)
            .setMaxConnPerRoute(20)
            .build()

        val httpClient: HttpClient = HttpClients.custom()
            .setDefaultRequestConfig(reqCfg)
            .setConnectionManager(cm)
            .build()

        val factory = HttpComponentsClientHttpRequestFactory(httpClient)
        return RestTemplate(factory)
    }

    private fun buildSslContext(): SSLContext {
        val ks = KeyStore.getInstance("PKCS12")
        certPath.inputStream.use { ks.load(it, certPassword.toCharArray()) }
        return SSLContexts.custom()
            .loadKeyMaterial(ks, certPassword.toCharArray())
            // Em produção, prefira um truststore oficial em vez de TrustAllStrategy.
            .loadTrustMaterial(TrustAllStrategy.INSTANCE)
            .build()
    }
}
