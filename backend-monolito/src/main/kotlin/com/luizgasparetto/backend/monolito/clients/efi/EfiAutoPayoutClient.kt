package com.luizgasparetto.backend.monolito.clients.efi

import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class EfiAutoPayoutClient(
    private val cfg: AutoPayoutConfig,
    @Qualifier("efiRestTemplate") private val http: RestTemplate, // mTLS
    private val auth: EfiAuthService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun baseUrl(): String =
        if (cfg.pix.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"

    private fun retrySleepMillis(headers: HttpHeaders, attempt: Int): Long {
        val retryAfter = headers.getFirst("Retry-After")?.toLongOrNull()
        val base = retryAfter?.times(1000) ?: (250L * (1 shl attempt))
        return base.coerceAtMost(8000L)
    }

    private fun <T> exchangeWithRetry(
        url: String,
        method: HttpMethod,
        entity: HttpEntity<*>,
        responseType: Class<T>,
        maxRetries: Int = 4
    ): ResponseEntity<T> {
        var attempt = 0
        while (true) {
            val resp = http.exchange(url, method, entity, responseType)
            val code = resp.statusCode.value()

            // Trace de rate-limit (quando vier na v3)
            resp.headers["Bucket-Size"]?.firstOrNull()?.let { log.debug("EFI rate-limit Bucket-Size={}", it) }
            resp.headers["Retry-After"]?.firstOrNull()?.let { log.debug("EFI rate-limit Retry-After={}", it) }

            if (code == 429 || code >= 500) {
                if (attempt >= maxRetries) return resp
                val sleep = retrySleepMillis(resp.headers, attempt)
                log.warn("EFI {} {} => {}. Retry {}/{} in {} ms", method, url, code, attempt + 1, maxRetries, sleep)
                try { Thread.sleep(sleep) } catch (_: InterruptedException) {}
                attempt++
                continue
            }
            return resp
        }
    }

    private fun <T> unwrapOrThrow(
        op: String,
        url: String,
        resp: ResponseEntity<T>
    ): T {
        val status = resp.statusCode
        if (status.is2xxSuccessful) {
            log.info("EFI {} OK: http={} url={} body={}", op, status.value(), url, resp.body)
            return resp.body ?: error("Empty body")
        }

        val body = resp.body
        val headers = resp.headers
        val msg = "EFI $op FAIL: http=${status.value()} url=$url body=$body"

        when (status.value()) {
            401, 403 -> throw EfiAuthException(msg, status, body, headers)
            429      -> {
                val ra = headers.getFirst("Retry-After")?.toLongOrNull()
                throw EfiRateLimitException(msg, ra, body, headers)
            }
            in 400..499 -> throw EfiClientException(msg, status, body, headers)
            else        -> throw EfiServerException(msg, status, body, headers)
        }
    }

    fun sendToKey(idempotencyId: String, payload: Map<String, Any>): Map<String, Any> {
        val url = "${baseUrl()}/v3/gn/pix/$idempotencyId" // v3: headers de rate-limit
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(auth.getAccessToken())
        }
        log.info("EFI SEND: PUT {} payload={}", url, payload)
        val resp = exchangeWithRetry(url, HttpMethod.PUT, HttpEntity(payload, headers), Map::class.java)
        @Suppress("UNCHECKED_CAST")
        return unwrapOrThrow("SEND", url, resp) as Map<String, Any>
    }

    fun listSent(fromIso: String, toIso: String, params: Map<String, Any?> = emptyMap()): Map<String, Any> {
        val qp = buildString {
            append("?inicio=$fromIso&fim=$toIso")
            params.forEach { (k, v) -> if (v != null) append("&$k=$v") }
        }
        val url = "${baseUrl()}/v2/gn/pix/enviados$qp"
        val headers = HttpHeaders().apply { setBearerAuth(auth.getAccessToken()) }
        log.info("EFI LIST SENT: GET {}", url)
        val resp = exchangeWithRetry(url, HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
        @Suppress("UNCHECKED_CAST")
        return unwrapOrThrow("LIST_SENT", url, resp) as Map<String, Any>
    }
}
