// src/main/kotlin/com/luizgasparetto/backend/monolito/clients/efi/EfiAuthService.kt
package com.luizgasparetto.backend.monolito.clients.efi

import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.*

@Service
class EfiAuthService(
    private val cfg: AutoPayoutConfig,
    @Qualifier("efiRestTemplate") private val rest: RestTemplate,
    @Qualifier("plainRestTemplate") private val plainRt: RestTemplate,
) {
    @Volatile private var token: String? = null
    @Volatile private var expiresAt: Instant? = null

    fun getAccessToken(): String {
        if (token != null && expiresAt?.isAfter(Instant.now().plusSeconds(30)) == true) {
            return token!!
        }
        return fetchNewToken()
    }

    private fun fetchNewToken(): String {
        val url = if (cfg.environment.equals("sandbox", true))
            "https://pix-h.api.efipay.com.br/oauth/token"
        else
            "https://pix.api.efipay.com.br/oauth/token"

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBasicAuth(cfg.clientId, cfg.clientSecret, Charsets.UTF_8)
        }

        val body = mapOf("grant_type" to "client_credentials")
        val req = HttpEntity(body, headers)
        val resp = rest.postForEntity(url, req, Map::class.java)

        if (!resp.statusCode.is2xxSuccessful) {
            throw IllegalStateException("EFI AUTH failed: HTTP=${resp.statusCode}")
        }

        val m = resp.body ?: emptyMap<String, Any>()
        val access = m["access_token"]?.toString() ?: error("No access_token")
        val ttl = (m["expires_in"] as? Number)?.toLong() ?: 600L

        token = access
        expiresAt = Instant.now().plusSeconds(ttl)
        return access
    }
}
