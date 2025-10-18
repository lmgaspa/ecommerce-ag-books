package com.luizgasparetto.backend.monolito.services.pix

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.config.efi.PixEfiProperties
import com.luizgasparetto.backend.monolito.dto.pix.PixCobrancaResponse
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.beans.factory.annotation.Qualifier
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PixService(
    private val auth: PixEfiAuthService,
    private val props: PixEfiProperties,
    @Qualifier("efiRestTemplate") private val rt: RestTemplate,
    private val mapper: ObjectMapper
) {
    /**
     * Cria/atualiza a cobrança Pix com o TXID fornecido e retorna o “copia e cola”
     * e a imagem (base64) do QR Code.
     */
    fun criarPixCobranca(
        valor: BigDecimal,
        chavePix: String,
        descricao: String,
        txid: String
    ): PixCobrancaResponse {
        val token = auth.getAccessToken()  // <-- sem enum agora
        val base = if (props.sandbox) "https://pix-h.api.efipay.com.br" else "https://pix.api.efipay.com.br"

        val valorStr = valor.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val body = mapOf(
            "calendario" to mapOf("expiracao" to 3600),
            "valor" to mapOf("original" to valorStr),
            "chave" to chavePix,
            "solicitacaoPagador" to descricao
        )

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(token)
        }

        // Cria/atualiza a cobrança usando o mesmo txid
        val resp = rt.exchange(
            "$base/v2/cob/$txid",
            HttpMethod.PUT,
            HttpEntity(body, headers),
            String::class.java
        )

        val root = mapper.readTree(resp.body)
        val locId = root.path("loc").path("id").asText(null) ?: error("loc.id não encontrado")

        val qr = rt.exchange(
            "$base/v2/loc/$locId/qrcode",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java
        )
        val qrJson = mapper.readTree(qr.body)

        return PixCobrancaResponse(
            pixCopiaECola = qrJson.path("qrcode").asText(""),
            imagemQrcodeBase64 = qrJson.path("imagemQrcode").asText(""),
            txid = txid
        )
    }
}
