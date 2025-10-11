package com.luizgasparetto.backend.monolito.services.autopayout

import com.luizgasparetto.backend.monolito.models.autopayout.response.AutoPayoutResponse
import java.time.OffsetDateTime

object EfiAutoPayoutMappers {

    private fun anyToIsoString(v: Any?): String? = when (v) {
        is OffsetDateTime -> v.toString()
        is String -> try { OffsetDateTime.parse(v).toString() } catch (_: Exception) { v }
        else -> null
    }

    fun mapBodyToAutoPayoutResponse(
        transferId: String,
        body: Map<String, Any?>
    ): AutoPayoutResponse {
        val horario = body["horario"] as? Map<*, *>
        val pagador = body["pagador"] as? Map<*, *>
        val favorecido = body["favorecido"] as? Map<*, *>
        val identificacao = favorecido?.get("identificacao") as? Map<*, *>
        val contaBanco = favorecido?.get("contaBanco") as? Map<*, *>
        val endToEnd = (body["e2eId"] ?: body["endToEndId"])?.toString()

        return AutoPayoutResponse(
            endToEndId       = endToEnd,
            transferId       = transferId,
            value            = body["valor"]?.toString(),
            payerKey         = pagador?.get("chave")?.toString(),
            payerInfo        = pagador?.get("infoPagador")?.toString(),
            status           = body["status"]?.toString(),
            requestedAt      = anyToIsoString(horario?.get("solicitacao")),
            settledAt        = anyToIsoString(horario?.get("liquidacao")),
            favoredKey       = favorecido?.get("chave")?.toString(),
            favoredName      = identificacao?.get("nome")?.toString(),
            favoredCpfMasked = identificacao?.get("cpf")?.toString(),
            favoredBankIspb  = contaBanco?.get("codigoBanco")?.toString(),
            raw              = body
        )
    }
}
