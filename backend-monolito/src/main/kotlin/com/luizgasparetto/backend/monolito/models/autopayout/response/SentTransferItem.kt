package com.luizgasparetto.backend.monolito.models.autopayout.response

import java.time.OffsetDateTime

data class SentTransferItem(
    val endToEndId: String?,
    val sendId: String?,
    val value: String,           // "12.34"
    val payerKey: String?,       // chave do pagador (sua conta Efí)
    val status: String,          // "REALIZADO", "EM_PROCESSAMENTO", "NAO_REALIZADO"
    val payerInfo: String?,      // infoPagador
    val timeRequested: OffsetDateTime?,
    val timeSettled: OffsetDateTime?,
    val favoredKey: String?,
    val favoredName: String?,
    val favoredCpfMasked: String?,
    val favoredBankIspb: String?
)
