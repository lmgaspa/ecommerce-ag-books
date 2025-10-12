package com.luizgasparetto.backend.monolito.payment.efi

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PaymentPixClient(
    private val paymentRestTemplate: RestTemplate,
    private val auth: PaymentEfiAuth,
    @Value("\${efi.pix.sandbox:false}") private val sandbox: Boolean
) {
    fun sendTransfer(idEnvio: String, pixKey: String, amount: BigDecimal) {
        val base = if (sandbox) "https://sandbox.efi.com.br" else "https://api.efi.com.br"

        // ⚠️ Ajuste esta rota e o payload para o endpoint de Pix de saída do seu contrato Efí.
        // Em muitos casos é algo como /v2/pix/… ou /pix/transfer orquestrado pela conta.
        val url = "$base/pix/enviar"

        val payload = mapOf(
            "idEnvio" to idEnvio,
            "chave"   to pixKey,
            "valor"   to amount.setScale(2, RoundingMode.HALF_UP).toString()
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(auth.bearer())
        }

        val res = paymentRestTemplate.postForEntity(url, HttpEntity(payload, headers), Map::class.java)
        if (!res.statusCode.is2xxSuccessful) {
            error("Pix send falhou: ${res.statusCode} ${res.body}")
        }
    }
}
