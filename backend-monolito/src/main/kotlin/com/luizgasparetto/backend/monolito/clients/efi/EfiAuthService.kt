package com.luizgasparetto.backend.monolito.clients.efi

import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
class EfiAuthService(
    private val cfg: AutoPayoutConfig,
    @Qualifier("efiRestTemplate") private val http: RestTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    @Volatile private var token: String? = null
    @Volatile private var expAt: Long = 0

    private fun baseUrl(): String =
        if (cfg.pix.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"

    fun getAccessToken(): String {
        val now = Instant.now().epochSecond
        if (!token.isNullOrBlank() && now < (expAt - 30)) return token!!

        val url = "${baseUrl()}/oauth/token"
        val scopes = listOf("pix.send", "gn.pix.send.read")
        val body = "grant_type=client_credentials&scope=" +
                URLEncoder.encode(scopes.joinToString(" "), StandardCharsets.UTF_8)

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            setBasicAuth(cfg.pix.clientId, cfg.pix.clientSecret)
        }

        log.info("EFI AUTH: requesting token (scopes={}) env={}", scopes, if (cfg.pix.sandbox) "sandbox" else "prod")
        val resp = http.exchange(url, HttpMethod.POST, HttpEntity(body, headers), Map::class.java)
        if (!resp.statusCode.is2xxSuccessful) {
            log.warn("EFI AUTH: http={} body={}", resp.statusCode, resp.body)
            error("EFI auth failed: ${resp.statusCode}")
        }

        @Suppress("UNCHECKED_CAST")
        val map = resp.body as Map<String, Any>
        token = map["access_token"] as? String ?: error("No access_token in auth response")
        val ttl = (map["expires_in"] as? Number)?.toLong() ?: 600L
        expAt = now + ttl
        log.info("EFI AUTH: token OK (ttl={}s)", ttl)
        return token!!
    }
}
