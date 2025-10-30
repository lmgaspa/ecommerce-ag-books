// src/main/kotlin/com/luizgasparetto/backend/monolito/payments/web/EfiPixPayoutProvider.kt
package com.luizgasparetto.backend.monolito.payments.web

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpPut
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.util.Timeout
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

@Profile("!stub") // usa este provider quando NÃO estiver no profile 'stub'
@Component
class EfiPixPayoutProvider(
    private val mapper: ObjectMapper,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean,
    @Value("\${efi.pix.chave}") private val pagadorChave: String,
    @Value("\${efi.pix.client-id}") private val clientId: String,
    @Value("\${efi.pix.client-secret}") private val clientSecret: String,
    @Value("\${efi.pix.cert-path}") private val certPath: String,
    @Value("\${efi.pix.cert-password:}") private val certPassword: String
) : PixPayoutProvider {

    override fun sendPixPayout(orderId: Long, amount: BigDecimal, favoredPixKey: String): String {
        // idEnvio deve seguir ^[a-zA-Z0-9]{1,35}$ (sem hífen/underscore)
        val idEnvio = "P$orderId"

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
                putPixSend(http, token, idEnvio, amount, favoredPixKey)
                return idEnvio
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

        // ✅ usa ResponseHandler (não-deprecated)
        val handler = HttpClientResponseHandler { resp ->
            val code = resp.code
            val jsonTxt = EntityUtils.toString(resp.entity, StandardCharsets.UTF_8)
            if (code !in listOf(200, 201)) {
                throw IllegalStateException("OAuth Efí HTTP $code: $jsonTxt")
            }
            val node: JsonNode = mapper.readTree(jsonTxt)
            val token = node.get("access_token")?.asText()
            if (token.isNullOrBlank()) {
                throw IllegalStateException("OAuth Efí sem access_token no payload: $jsonTxt")
            }
            token
        }

        return http.execute(post, handler)
    }

    private fun putPixSend(
        http: CloseableHttpClient,
        token: String,
        idEnvio: String,
        amount: BigDecimal,
        favoredPixKey: String
    ) {
        val base = if (sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val url = "$base/v3/gn/pix/$idEnvio"

        // Monta favorecido + validação opcional cpf/cnpj se a chave "parece" doc
        val favorecido = mutableMapOf<String, Any>(
            "chave" to favoredPixKey
        )
        val onlyDigits = favoredPixKey.filter { it.isDigit() }
        when (onlyDigits.length) {
            11 -> favorecido["cpf"] = onlyDigits
            14 -> favorecido["cnpj"] = onlyDigits
        }

        val body = mapOf(
            "valor" to amount.setScale(2).toPlainString(),
            "pagador" to mapOf(
                "chave" to pagadorChave,
                "infoPagador" to "Repasse pedido $idEnvio"
            ),
            "favorecido" to favorecido
        )

        val put = HttpPut(url).apply {
            addHeader("Authorization", "Bearer $token")
            addHeader("Content-Type", "application/json")
            addHeader("Accept", "application/json")
            entity = StringEntity(
                mapper.writeValueAsString(body),
                ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8)
            )
        }

        val handler = HttpClientResponseHandler<Unit> { resp ->
            val code = resp.code
            val bodyTxt = EntityUtils.toString(resp.entity, StandardCharsets.UTF_8)
            // 201 = criado (EM_PROCESSAMENTO); 409 = idempotente (já enviado) → OK
            if (code == 201 || code == 409) return@HttpClientResponseHandler
            throw IllegalStateException("Efí PIX send HTTP $code: $bodyTxt")
        }

        http.execute(put, handler)
    }

    private fun buildSslContext(): SSLContext {
        // Carrega o .p12 tanto de "classpath:" quanto de caminho absoluto/relativo
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
