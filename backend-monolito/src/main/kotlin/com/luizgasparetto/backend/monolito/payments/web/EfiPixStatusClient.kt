package com.luizgasparetto.backend.monolito.payments.web

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.util.Timeout
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

data class PixSendStatus(
    val idEnvio: String,
    val status: String,           // EM_PROCESSAMENTO | REALIZADO | NAO_REALIZADO
    val endToEndId: String? = null,
    val txid: String? = null,
    val efetivadoEm: String? = null // ISO string da Efí, se vier
)

/**
 * Cliente somente-leitura para consultar status do envio Pix (pix-send).
 * Não interfere no provider de envio; é uma peça separada para reconciliar estados.
 */
@Profile("!stub")
@Component
class EfiPixStatusClient(
    private val mapper: ObjectMapper,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean,
    @Value("\${efi.pix.client-id}") private val clientId: String,
    @Value("\${efi.pix.client-secret}") private val clientSecret: String,
    @Value("\${efi.pix.cert-path}") private val certPath: String,
    @Value("\${efi.pix.cert-password:}") private val certPassword: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getSendStatus(idEnvio: String): PixSendStatus {
        val sslContext = buildSslContext()
        val sslSocketFactory = SSLConnectionSocketFactory(sslContext)

        val connectionConfig: ConnectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(15))
            .build()

        val cm = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslSocketFactory)
            .setDefaultConnectionConfig(connectionConfig)
            .build()

        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(15))
            .setResponseTimeout(Timeout.ofSeconds(30))
            .build()

        HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(requestConfig)
            .build().use { http ->
                val token = fetchAccessToken(http)
                return queryStatus(http, token, idEnvio)
            }
    }

    private fun fetchAccessToken(http: CloseableHttpClient): String {
        val base = if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val url = "$base/oauth/token"

        val post = HttpPost(url).apply {
            val body = mapper.writeValueAsString(mapOf("grant_type" to "client_credentials"))
            entity = StringEntity(body, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8))
            val basic = java.util.Base64.getEncoder()
                .encodeToString("$clientId:$clientSecret".toByteArray(StandardCharsets.UTF_8))
            addHeader("Authorization", "Basic $basic")
            addHeader("Accept", "application/json")
        }

        http.execute(post).use { resp ->
            val code = resp.code
            val jsonTxt = resp.entity?.content?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            if (code !in listOf(200, 201)) {
                throw IllegalStateException("OAuth Efí HTTP $code: $jsonTxt")
            }
            val node: JsonNode = mapper.readTree(jsonTxt)
            return node.get("access_token")?.asText().orEmpty()
                .takeIf { it.isNotBlank() }
                ?: error("OAuth Efí sem access_token no payload: $jsonTxt")
        }
    }

    private fun queryStatus(http: CloseableHttpClient, token: String, idEnvio: String): PixSendStatus {
        val base = if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val url = "$base/v3/gn/pix/$idEnvio"

        val get = HttpGet(url).apply {
            addHeader("Authorization", "Bearer $token")
            addHeader("Accept", "application/json")
        }

        http.execute(get).use { resp ->
            val code = resp.code
            val bodyTxt = resp.entity?.content?.readBytes()?.toString(Charsets.UTF_8).orEmpty()
            if (code !in listOf(200)) {
                throw IllegalStateException("Efí PIX status HTTP $code: $bodyTxt")
            }
            val node = mapper.readTree(bodyTxt)
            val status = node.path("status").asText().ifBlank { "EM_PROCESSAMENTO" }
            val endToEnd = node.path("e2eId").asText(null) ?: node.path("endToEndId").asText(null)
            val txid = node.path("txid").asText(null)
            val efetivadoEm = node.path("horario").path("efetivacao").asText(null)
            return PixSendStatus(idEnvio, status.uppercase(), endToEnd, txid, efetivadoEm)
        }
    }

    private fun buildSslContext(): SSLContext {
        val input: InputStream = if (certPath.startsWith("classpath:")) {
            val resPath = certPath.removePrefix("classpath:")
            val stream = this::class.java.classLoader.getResourceAsStream(resPath)
            requireNotNull(stream) { "Certificado não encontrado no classpath: $resPath" }
            stream
        } else {
            java.nio.file.Files.newInputStream(java.nio.file.Paths.get(certPath))
        }

        input.use { ins ->
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(ins, certPassword.toCharArray())

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, certPassword.toCharArray())

            return SSLContexts.custom()
                .loadKeyMaterial(ks, certPassword.toCharArray())
                .build()
        }
    }
}
