// src/main/kotlin/com/luizgasparetto/backend/monolito/services/autopayout/SentTransfersQueryService.kt
package com.luizgasparetto.backend.monolito.services.autopayout

import com.luizgasparetto.backend.monolito.clients.efi.EfiAutoPayoutClient
import com.luizgasparetto.backend.monolito.models.autopayout.response.SentTransferItem
import com.luizgasparetto.backend.monolito.models.autopayout.response.SentTransfersPage
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max

@Service
class SentTransfersQueryService(
    private val efiAutoPayoutClient: EfiAutoPayoutClient
) {
    private val rfc3339 = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun listSent(
        fromDate: OffsetDateTime,
        toDate: OffsetDateTime,
        status: String? = null,
        page: Int = 0,
        pageSize: Int = 100
    ): SentTransfersPage<SentTransferItem> {

        val fromIso = rfc3339.format(fromDate)
        val toIso = rfc3339.format(toDate)

        val params: MutableMap<String, Any?> = linkedMapOf(
            "paginacao.paginaAtual" to page,
            "paginacao.itensPorPagina" to pageSize
        )
        if (!status.isNullOrBlank()) params["status"] = status

        val raw: Map<String, Any> = efiAutoPayoutClient.listSent(fromIso, toIso, params)

        // A Efí pode retornar a lista em diferentes chaves dependendo do endpoint/versão.
        // Tentamos nas chaves mais comuns; se não houver, cai para lista vazia.
        @Suppress("UNCHECKED_CAST")
        val rows: List<Map<String, Any?>> =
            (raw["pix"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?:
            (raw["data"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?:
            (raw["itens"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?:
            emptyList()

        val items = rows.map { row ->
            val horario = row["horario"] as? Map<*, *>
            val fav = row["favorecido"] as? Map<*, *>
            val favId = fav?.get("identificacao") as? Map<*, *>
            val favBank = fav?.get("contaBanco") as? Map<*, *>

            SentTransferItem(
                endToEndId       = row["endToEndId"] as? String,
                sendId           = row["idEnvio"] as? String,
                value            = (row["valor"] as? String) ?: "0.00",
                payerKey         = row["chave"] as? String,
                status           = (row["status"] as? String) ?: "EM_PROCESSAMENTO",
                payerInfo        = row["infoPagador"] as? String,
                timeRequested    = (horario?.get("solicitacao") as? String)?.let { OffsetDateTime.parse(it) },
                timeSettled      = (horario?.get("liquidacao") as? String)?.let { OffsetDateTime.parse(it) },
                favoredKey       = fav?.get("chave") as? String,
                favoredName      = favId?.get("nome") as? String,
                favoredCpfMasked = favId?.get("cpf") as? String,
                favoredBankIspb  = favBank?.get("codigoBanco") as? String
            )
        }

        @Suppress("UNCHECKED_CAST")
        val pg = raw["paginacao"] as? Map<String, Any?>

        val totalItems: Long =
            (pg?.get("quantidadeTotalRegistros") as? Number)?.toLong() ?: items.size.toLong()

        val totalPages: Int =
            (pg?.get("quantidadeDePaginas") as? Number)?.toInt()
                ?: max(1, ceil(totalItems.toDouble() / pageSize).toInt())

        return SentTransfersPage(
            content    = items,
            page       = page,
            pageSize   = pageSize,
            totalItems = totalItems,
            totalPages = totalPages
        )
    }
}
