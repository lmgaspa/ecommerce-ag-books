package com.luizgasparetto.backend.monolito.models.autopayout.request

/**
 * Pedido de repasse Pix (envio).
 *
 * @param amountBRL valor monetário no formato "123.45"
 * @param favoredKey chave Pix do recebedor (opcional; se não vier, usa ENV: efi.payout.favored-key)
 * @param message texto curto do pagador (aparece no extrato)
 * @param idempotencyId opcional; se não vier, usa o transferId da URL
 */
data class AutoPayoutRequest(
    val amountBRL: String,
    val favoredKey: String? = null,
    val message: String? = null,
    val idempotencyId: String? = null
)
