// src/main/kotlin/com/luizgasparetto/backend/monolito/api/payout/PayoutPreviewReq.kt
package com.luizgasparetto.backend.monolito.api.payout

import java.math.BigDecimal

data class PayoutPreviewReq(
    /** Total bruto recebido (ex.: soma de pedidos pagos no período) */
    val gross: BigDecimal,

    /** Frete cobrado (ex.: soma do shipping dos pedidos) */
    val shipping: BigDecimal? = BigDecimal.ZERO,

    /** Se true, frete entra na base; se false, é excluído antes do cálculo */
    val includeShippingInBase: Boolean = false,

    /** Total de devoluções (se 'refunds' não vier) */
    val refundsTotal: BigDecimal? = BigDecimal.ZERO,

    /** Lista de devoluções individuais (se vier, soma substitui refundsTotal) */
    val refunds: List<BigDecimal>? = null,

    /** Overrides opcionais (se omitidos, usa os defaults do application.yml) */
    val overrideFeePercent: BigDecimal? = null,
    val overrideFeeFixed: BigDecimal? = null,
    val overrideMarginPercent: BigDecimal? = null,
    val overrideMarginFixed: BigDecimal? = null
)
