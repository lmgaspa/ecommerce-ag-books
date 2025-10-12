package com.luizgasparetto.backend.monolito.payment.efi

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.*

@Component
class PaymentEfiAuth(
    private val paymentRestTemplate: RestTemplate,
    @Value("\${efi.pix.client-id}") private val clientId: String,
    @Value("\${efi.pix.client-secret}") private val clientSecret: String,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean
) {
    @Volatile private var cachedToken: String? = null
    @Volatile private var expiresAt: Instant? = null

    fun bearer(): String {
        val now = Instant.now()
        if (cachedToken != null && expiresAt?.isAfter(now.plusSeconds(30)) == true) return cachedToken!!
        val url = if (sandbox) "https://sandbox.efi.com.br/oauth/token" else "https://api.efi.com.br/oauth/token"

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val body = mapOf(
            "grant_type" to "client_credentials",
            "client_id" to clientId,
            "client_secret" to clientSecret
        )
        val res = paymentRestTemplate.postForEntity(url, HttpEntity(body, headers), JsonNode::class.java)
        if (!res.statusCode.is2xxSuccessful) error("OAuth Efí falhou: ${res.statusCode}")

        val json = res.body!!
        val token = json["access_token"]?.asText() ?: error("access_token ausente")
        val ttl = json["expires_in"]?.asLong() ?: 300L
        cachedToken = token
        expiresAt = Instant.now().plusSeconds(ttl - 30)
        return token
    }
}
