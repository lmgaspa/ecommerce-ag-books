// src/main/kotlin/.../efi/PixSendClient.kt
package com.luizgasparetto.backend.monolito.efi

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

@Component
class PixSendClient(
    private val efiAuth: EfiAuthClient,
    private val efiWc: WebClient,
    @Value("\${efi.pix.chave}") private val pagadorChave: String
) {
    data class SendBody(
        val valor: String,
        val pagador: Map<String, String>,
        val favorecido: Map<String, String>
    )
    data class SendRes(
        val endToEndId: String?,
        val idEnvio: String?,
        val status: String?
    )

    private fun fmt2(v: BigDecimal): String {
        val dfs = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("0.00", dfs); df.isParseBigDecimal = true
        return df.format(v)
    }

    fun enviar(idEnvio: String, valor: BigDecimal, favorecidoChave: String, info: String? = null): SendRes {
        val token = efiAuth.token()
        val body = SendBody(
            valor = fmt2(valor),
            pagador = buildMap {
                put("chave", pagadorChave)
                info?.let { put("infoPagador", it.take(140)) }
            },
            favorecido = mapOf("chave" to favorecidoChave)
        )

        return efiWc.put()
            .uri("/v3/gn/pix/{idEnvio}", idEnvio)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $token")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(SendRes::class.java)
            .block() ?: SendRes(null, idEnvio, "DESCONHECIDO")
    }
}
