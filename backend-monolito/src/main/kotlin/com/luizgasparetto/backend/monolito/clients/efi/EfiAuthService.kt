package com.luizgasparetto.backend.monolito.clients.efi

import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Base64

@Component
class EfiAuthService(
    private val cfg: AutoPayoutConfig,
    @Qualifier("efiRestTemplate") private val http: RestTemplate
) {
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAt: Instant = Instant.EPOCH

    private fun baseUrl(): String =
        if (cfg.environment.equals("sandbox", true)) "https://pix-h.api.efipay.com.br"
        else "https://pix.api.efipay.com.br"

    @Synchronized
    fun getAccessToken(): String {
        val now = Instant.now()
        if (cachedToken != null && now.isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken!!
        }

        val url = "${baseUrl()}/oauth/token"
        val body = mapOf("grant_type" to "client_credentials")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            val basic = Base64.getEncoder()
                .encodeToString("${cfg.clientId}:${cfg.clientSecret}".toByteArray())
            set("Authorization", "Basic $basic")
        }

        val req = HttpEntity(body, headers)
        val resp = http.postForEntity(url, req, Map::class.java)

        if (!resp.statusCode.is2xxSuccessful) {
            error("EFI AUTH failed: ${resp.statusCode} ${resp.body}")
        }

        @Suppress("UNCHECKED_CAST")
        val map = resp.body as Map<String, Any>
        val token = map["access_token"]?.toString() ?: error("No access_token in response")
        val ttl = (map["expires_in"] as? Number)?.toLong() ?: 300L

        cachedToken = token
        expiresAt = Instant.now().plusSeconds(ttl)
        return token
    }
}
