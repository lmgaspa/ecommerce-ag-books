// src/main/kotlin/com/luizgasparetto/backend/monolito/clients/efi/EfiAutoPayoutClient.kt
package com.luizgasparetto.backend.monolito.clients.efi

import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class EfiAutoPayoutClient(
    private val cfg: AutoPayoutConfig,
    @Qualifier("efiRestTemplate") private val http: RestTemplate,   // usa SEMPRE o mTLS
    private val auth: EfiAuthService
    // Se precisar do RT “sem mTLS” no futuro, pode injetar assim:
    // @Qualifier("plainRestTemplate") private val plainRt: RestTemplate
) {
    private fun baseUrl(): String =
        if (cfg.environment.equals("sandbox", true)) "https://pix-h.api.efipay.com.br"
        else "https://pix.api.efipay.com.br"

    fun sendToKey(idempotencyId: String, payload: Map<String, Any>): Map<String, Any> {
        val url = "${baseUrl()}/v2/gn/pix/${idempotencyId}"
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(auth.getAccessToken())
        }
        val req = HttpEntity(payload, headers)
        val resp = http.exchange(url, HttpMethod.PUT, req, Map::class.java)  // <<< usa http (mTLS)
        if (!resp.statusCode.is2xxSuccessful) error("EFI send failed: ${resp.statusCode}")
        @Suppress("UNCHECKED_CAST")
        return resp.body as Map<String, Any>
    }

    fun listSent(fromIso: String, toIso: String, params: Map<String, Any?> = emptyMap()): Map<String, Any> {
        val qp = buildString {
            append("?inicio=$fromIso&fim=$toIso")
            params.forEach { (k, v) -> if (v != null) append("&$k=$v") }
        }
        val url = "${baseUrl()}/v2/gn/pix/enviados$qp"
        val headers = HttpHeaders().apply { setBearerAuth(auth.getAccessToken()) }
        val req = HttpEntity<Void>(headers)
        val resp = http.exchange(url, HttpMethod.GET, req, Map::class.java)  // <<< usa http (mTLS)
        if (!resp.statusCode.is2xxSuccessful) error("EFI list sent failed: ${resp.statusCode}")
        @Suppress("UNCHECKED_CAST")
        return resp.body as Map<String, Any>
    }
}
