package com.luizgasparetto.backend.monolito.services.card

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.CardEfiProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate

@Component
class CardClient(
    private val auth: CardEfiAuthService,
    private val props: CardEfiProperties,
    @Qualifier("plainRestTemplate") private val rt: RestTemplate,
    private val mapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(CardClient::class.java)

    private fun baseUrl(): String =
        if (props.sandbox) "https://cobrancas-h.api.efipay.com.br"
        else "https://cobrancas.api.efipay.com.br"

    private fun bearerHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(auth.getAccessToken())
    }

    /** POST /v1/charge/one-step  */
    fun oneStep(body: Map<String, Any>): JsonNode {
        val url = "${baseUrl()}/v1/charge/one-step"
        val headers = bearerHeaders()
        try {
            val resp = rt.exchange(url, HttpMethod.POST, HttpEntity(body, headers), String::class.java)
            return mapper.readTree(resp.body ?: "{}")
        } catch (e: HttpStatusCodeException) {
            val response = e.responseBodyAsString
            log.warn("CARD one-step: HTTP={} body={}", e.statusCode, response)
            throw e
        }
    }

    /** GET /v1/charge/{chargeId} */
    fun getCharge(chargeId: String): JsonNode {
        val url = "${baseUrl()}/v1/charge/$chargeId"
        val headers = HttpHeaders().apply { setBearerAuth(auth.getAccessToken()) }
        val resp = rt.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        return mapper.readTree(resp.body ?: "{}")
    }

    /** POST /v1/charge/{chargeId}/cancel */
    fun cancel(chargeId: String): Boolean {
        val url = "${baseUrl()}/v1/charge/$chargeId/cancel"
        val headers = bearerHeaders()
        return try {
            val resp = rt.exchange(url, HttpMethod.POST, HttpEntity(emptyMap<String, Any>(), headers), String::class.java)
            resp.statusCode.is2xxSuccessful
        } catch (e: HttpClientErrorException.NotFound) {
            // 404 significa que o charge já foi cancelado ou não existe mais
            // Isso é um caso esperado, então retornamos true (objetivo alcançado: charge não está ativo)
            log.debug("CARD cancel: chargeId={} não encontrado (já cancelado ou não existe)", chargeId)
            true
        } catch (e: HttpStatusCodeException) {
            log.warn("CARD cancel: HTTP={} chargeId={} body={}", e.statusCode, chargeId, e.responseBodyAsString)
            false
        }
    }
}
