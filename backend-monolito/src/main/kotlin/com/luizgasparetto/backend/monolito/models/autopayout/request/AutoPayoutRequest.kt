package com.luizgasparetto.backend.monolito.models.autopayout.request

/**
 * Request para envio de repasse via PIX (Efí) pelo endpoint:
 * PUT /v3/gn/pix/{idEnvio}
 */
data class AutoPayoutRequest(
    /** Valor em BRL com 2 casas, ex.: "12.34" */
    val amountBRL: String,

    /** Chave PIX do favorecido. Se nulo, usa a da ENV (efi.payout.favored-key). */
    val favoredKey: String? = null,

    /** Texto curto que aparece no extrato do pagador */
    val message: String? = null
)
