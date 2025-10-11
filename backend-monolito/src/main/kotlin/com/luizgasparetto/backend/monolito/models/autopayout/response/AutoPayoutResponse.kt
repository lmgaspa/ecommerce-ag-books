package com.luizgasparetto.backend.monolito.models.autopayout.response

/**
 * Resposta “flattened” do envio de PIX (repasse automático).
 * Mapeia os campos relevantes retornados pela Efí.
 */
data class AutoPayoutResponse(
    val endToEndId: String?,
    val transferId: String,          // idEnvio usado (idempotência)
    val value: String?,              // "valor" (string com 2 casas)
    val payerKey: String?,           // chave do pagador (sua chave Efí)
    val payerInfo: String?,          // infoPagador
    val status: String?,             // EM_PROCESSAMENTO | REALIZADO | NAO_REALIZADO
    val requestedAt: String?,        // horario.solicitacao
    val settledAt: String?,          // horario.liquidacao (se houver)
    val favoredKey: String?,         // chave do recebedor
    val favoredName: String?,        // favorecido.identificacao.nome (se vier)
    val favoredCpfMasked: String?,   // favorecido.identificacao.cpf (mascarado)
    val favoredBankIspb: String?,    // favorecido.contaBanco.codigoBanco
    val raw: Map<String, Any?>       // corpo bruto para auditoria/diagnóstico
)
