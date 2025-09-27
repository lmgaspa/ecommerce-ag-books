package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

@Service
class CardEfiAuthService(
    private val props: CardEfiProperties,
    private val plainRestTemplate: RestTemplate // sem mTLS
) {

    private val log = LoggerFactory.getLogger(CardEfiAuthService::class.java)

    private data class Token(val accessToken: String, val expiresAtMs: Long)
    private val cached = AtomicReference<Token?>(null)

    private fun authorizeUrl(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br/v1/authorize"
        else               "https://cobrancas.api.efipay.com.br/v1/authorize"

    /** Base dos endpoints de CHARGES (para criar/consultar/cancelar cobranças). */
    fun chargesBaseUrl(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br"
        else               "https://cobrancas.api.efipay.com.br"

    /** Retorna um bearer token válido para as chamadas de cartão. */
    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        cached.get()?.let { tok ->
            if (tok.expiresAtMs - 5_000 > now) return tok.accessToken
        }
        return fetchNewToken()
    }

    private fun fetchNewToken(): String {
        val url = authorizeUrl()

        val basic = Base64.getEncoder().encodeToString(
            "${props.clientId}:${props.clientSecret}".toByteArray(StandardCharsets.UTF_8)
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set(HttpHeaders.AUTHORIZATION, "Basic $basic")
        }

        val body = mapOf("grant_type" to "client_credentials")

        try {
            val resp = plainRestTemplate.postForEntity(url, HttpEntity(body, headers), Map::class.java)
            val map = resp.body ?: error("Resposta vazia do /v1/authorize")
            val token = (map["access_token"] as? String).orEmpty()
            val expiresIn = (map["expires_in"] as? Number)?.toLong() ?: 600L
            require(token.isNotBlank()) { "access_token ausente na resposta" }

            cached.set(Token(token, System.currentTimeMillis() + expiresIn * 1000))
            log.info(
                "EFI CARD AUTH OK: sandbox={}, clientId=***{}",
                props.sandbox,
                props.clientId.takeLast(4)
            )
            return token
        } catch (e: HttpStatusCodeException) {
            // 401 geralmente = credenciais inválidas ou app/escopo não habilitado na Efí
            log.warn(
                "EFI CARD AUTH FAIL {} url={} body={}",
                e.statusCode, url, e.responseBodyAsString
            )
            throw IllegalStateException("Falha ao obter token de cartão: ${e.statusCode}", e)
        } catch (e: Exception) {
            log.warn("EFI CARD AUTH: falha ao chamar {}: {}", url, e.message)
            throw IllegalStateException("Falha ao obter token de cartão: ${e.message}", e)
        }
    }
}
