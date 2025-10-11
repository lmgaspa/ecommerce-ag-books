// src/main/kotlin/com/luizgasparetto/backend/monolito/models/autopayout/response/SentTransfersPage.kt
package com.luizgasparetto.backend.monolito.models.autopayout.response

data class SentTransfersPage(
    val items: List<SentTransferItem>,
    val currentPage: Int?,
    val pageSize: Int?
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(m: Map<String, Any>): SentTransfersPage {
            val list = (m["pixEnviados"] as? List<Map<String, Any>>)?.map {
                SentTransferItem(
                    endToEndId = it["endToEndId"]?.toString(),
                    transferId = it["idEnvio"]?.toString(),
                    amount = it["valor"]?.toString(),
                    status = it["status"]?.toString(),
                    requestedAt = (it["horario"] as? Map<*, *>)?.get("solicitacao")?.toString(),
                    settledAt = (it["horario"] as? Map<*, *>)?.get("liquidacao")?.toString()
                )
            } ?: emptyList()
            val pag = m["paginacao"] as? Map<String, Any>
            return SentTransfersPage(
                items = list,
                currentPage = (pag?.get("paginaAtual") as? Number)?.toInt(),
                pageSize = (pag?.get("itensPorPagina") as? Number)?.toInt()
            )
        }
    }
}
