// src/main/kotlin/.../efi/EfiAuthClient.kt
package com.luizgasparetto.backend.monolito.efi

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.*

@Component
class EfiAuthClient(
    @Value("\${efi.pix.client-id}") private val clientId: String,
    @Value("\${efi.pix.client-secret}") private val clientSecret: String,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean
) {
    private val base = if (sandbox) "https://api-pix-h.efipay.com.br" else "https://pix.api.efipay.com.br"
    private val wc = WebClient.builder().baseUrl(base).build()

    data class TokenRes(val access_token: String, val token_type: String, val expires_in: Long)

    fun token(): String {
        val basic = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val res = wc.post()
            .uri("/oauth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Basic $basic")
            .bodyValue("""{"grant_type":"client_credentials"}""")
            .retrieve()
            .bodyToMono(TokenRes::class.java)
            .block() ?: throw IllegalStateException("Sem token da Efí")
        return res.access_token
    }
}
