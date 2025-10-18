package com.luizgasparetto.backend.monolito.services.pix

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.PixEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Service
class PixEfiAuthService(
    private val props: PixEfiProperties, // efi.pix.*
    private val mapper: ObjectMapper,
    @Qualifier("efiRestTemplate") private val rtPix: RestTemplate // mTLS habilitado via cert-path/cert-password
) {
    private val log = LoggerFactory.getLogger(PixEfiAuthService::class.java)

    @Volatile private var token: String? = null
    @Volatile private var expMillis: Long = 0

    fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        token?.let { cached ->
            if (now < expMillis && cached.isNotBlank()) return cached
        }
        val (newToken, expSeconds) = fetchNewToken()
        token = newToken
        // protege contra clock skew e respostas sem expires coerentes
        expMillis = now + (expSeconds - 10).coerceAtLeast(30) * 1000L
        return newToken
    }

    /** Pair<access_token, expires_inSeconds> */
    private fun fetchNewToken(): Pair<String, Int> {
        val clientId = props.clientId
        val clientSecret = props.clientSecret

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            setBasicAuth(clientId, clientSecret)
        }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
        }

        val host = if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"
        val url = "$host/oauth/token"

        return try {
            val resp = rtPix.postForEntity(url, HttpEntity(form, headers), String::class.java)
            if (!resp.statusCode.is2xxSuccessful) {
                log.warn("EFI PIX AUTH: HTTP={} url={} body={}", resp.statusCode, url, resp.body)
                throw IllegalStateException("Pix OAuth falhou: HTTP=${resp.statusCode}")
            }
            val json: JsonNode = mapper.readTree(resp.body ?: "{}")
            val token = json.path("access_token").asText(null)
                ?: json.path("token").asText(null)
            val exp = json.path("expires_in").asInt(3600)

            if (token.isNullOrBlank()) {
                log.warn("EFI PIX AUTH: resposta sem token url={} body={}", url, resp.body)
                throw IllegalStateException("Pix OAuth sem token")
            }
            log.info("EFI PIX AUTH OK: url={}", url)
            token to exp
        } catch (e: HttpStatusCodeException) {
            log.warn("EFI PIX AUTH: HTTP={} url={} body={}", e.statusCode, url, e.responseBodyAsString)
            throw e
        } catch (e: Exception) {
            log.warn("EFI PIX AUTH: falha ao chamar {}: {}", url, e.message)
            throw e
        }
    }
}
