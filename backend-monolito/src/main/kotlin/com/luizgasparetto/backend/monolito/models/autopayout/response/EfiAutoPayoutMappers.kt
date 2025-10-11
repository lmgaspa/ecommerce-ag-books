package com.luizgasparetto.backend.monolito.models.autopayout.response

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Suppress("UNCHECKED_CAST")
fun Map<String, Any>.toAutoPayoutResponse(): AutoPayoutResponse {
    val endToEndId = this["endToEndId"]?.toString()
    val transferId = this["idEnvio"]?.toString() ?: ""           // idempotencyId
    val value = this["valor"]?.toString() ?: "0.00"
    val payerKey = this["chave"]?.toString()
    val status = this["status"]?.toString() ?: "EM_PROCESSAMENTO"
    val payerInfo = this["infoPagador"]?.toString()

    val horario = this["horario"] as? Map<String, Any?>
    val solicitacao = horario?.get("solicitacao")?.toString()
    val liquidacao = horario?.get("liquidacao")?.toString()

    val favored = this["favorecido"] as? Map<String, Any?>
    val favoredKey = favored?.get("chave")?.toString()
    val identificacao = favored?.get("identificacao") as? Map<String, Any?>
    val favoredName = identificacao?.get("nome")?.toString()
    val favoredCpfMasked = identificacao?.get("cpf")?.toString()
    val contaBanco = favored?.get("contaBanco") as? Map<String, Any?>
    val favoredBankIspb = contaBanco?.get("codigoBanco")?.toString()

    fun parseIso(s: String?): OffsetDateTime? =
        s?.let {
            try { OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME) }
            catch (_: Exception) { null }
        }

    return AutoPayoutResponse(
        endToEndId = endToEndId,
        transferId = transferId,
        value = value,
        payerKey = payerKey,
        status = status,
        payerInfo = payerInfo,
        requestedAt = parseIso(solicitacao),
        settledAt = parseIso(liquidacao),
        favoredKey = favoredKey,
        favoredName = favoredName,
        favoredCpfMasked = favoredCpfMasked,
        favoredBankIspb = favoredBankIspb
    )
}
