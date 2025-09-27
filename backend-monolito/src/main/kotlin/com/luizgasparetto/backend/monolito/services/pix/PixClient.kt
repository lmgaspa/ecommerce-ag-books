package com.luizgasparetto.backend.monolito.services.pix

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.PixEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class PixClient(
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val auth: PixEfiAuthService,
    private val props: PixEfiProperties,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(PixClient::class.java)

    private fun baseUrl(): String =
        if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"

    fun getCobranca(txid: String): JsonNode {
        val url = "${baseUrl()}/v2/cob/$txid"
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply { setBearerAuth(token) }
        val res = rt.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        val body = res.body ?: "{}"
        return mapper.readTree(body)
    }

    fun cancel(txid: String): Boolean {
        val url = "${baseUrl()}/v2/cob/$txid"
        val token = auth.getAccessToken()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }
        // Muitos PSPs aceitam PATCH status = REMOVIDA_PELO_USUARIO_RECEBEDOR
        val body = mapOf("status" to "REMOVIDA_PELO_USUARIO_RECEBEDOR")
        val res = rt.exchange(url, HttpMethod.PATCH, HttpEntity(body, headers), String::class.java)
        return res.statusCode.is2xxSuccessful
    }

    fun status(txid: String): String? =
        getCobranca(txid).path("status").asText(null)
}
