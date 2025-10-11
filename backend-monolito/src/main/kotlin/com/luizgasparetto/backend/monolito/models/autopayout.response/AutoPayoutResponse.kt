// src/main/kotlin/com/luizgasparetto/backend/monolito/models/autopayout/response/AutoPayoutResponse.kt
package com.luizgasparetto.backend.monolito.models.autopayout.response

data class AutoPayoutResponse(
    val endToEndId: String?,
    val idEnvio: String?,
    val valor: String?,
    val chave: String?,           // chave do pagador
    val status: String?,
    val infoPagador: String?,
    val favorecidoChave: String?
) {
    companion object {
        fun from(raw: Map<String, Any?>): AutoPayoutResponse = AutoPayoutResponse(
            endToEndId = raw["endToEndId"] as? String,
            idEnvio = raw["idEnvio"] as? String,
            valor = raw["valor"] as? String,
            chave = raw["chave"] as? String,
            status = raw["status"] as? String,
            infoPagador = raw["infoPagador"] as? String,
            favorecidoChave = (raw["favorecido"] as? Map<*, *>)?.get("chave") as? String
        )
    }
}
