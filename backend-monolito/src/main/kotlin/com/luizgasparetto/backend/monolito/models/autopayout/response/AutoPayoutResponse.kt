package com.luizgasparetto.backend.monolito.models.autopayout.response

import java.time.OffsetDateTime

/**
 * Modelo normalizado do retorno do envio de Pix (repasse).
 * Baseado no exemplo oficial da Efí:
 * {
 *   "endToEndId": "...",
 *   "idEnvio": "identificadorEnvio...",
 *   "valor": "0.01",
 *   "chave": "19974764017", // pagador
 *   "status": "REALIZADO",
 *   "infoPagador": "...",
 *   "horario": {"solicitacao":"...","liquidacao":"..."},
 *   "favorecido": {
 *     "chave":"francisco@...","identificacao":{"nome":"...","cpf":"***.456.789-**"},
 *     "contaBanco":{"codigoBanco":"09089356"}
 *   }
 * }
 */
data class AutoPayoutResponse(
    val endToEndId: String?,
    /** idEnvio (idempotencyId) que você usou no PUT */
    val transferId: String,
    /** "valor" string no formato 123.45 */
    val value: String,
    /** chave do pagador (sua chave/conta Efí) */
    val payerKey: String?,
    /** "EM_PROCESSAMENTO" | "REALIZADO" | "NAO_REALIZADO" (ou outros que surgirem) */
    val status: String,
    val payerInfo: String?,
    /** horario.solicitacao */
    val requestedAt: OffsetDateTime?,
    /** horario.liquidacao (quando houver) */
    val settledAt: OffsetDateTime?,
    /** chave informada do favorecido */
    val favoredKey: String?,
    val favoredName: String?,
    /** cpf mascarado do favorecido (se vier) */
    val favoredCpfMasked: String?,
    /** ISPB do banco favorecido (contaBanco.codigoBanco) */
    val favoredBankIspb: String?
)
