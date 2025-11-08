package com.luizgasparetto.backend.monolito.ga4.dto

data class FunnelDTO(
    val viewItem: Long,
    val addToCart: Long,
    val beginCheckout: Long,
    val purchase: Long
) {
    val cartRate get() = rate(addToCart, viewItem)
    val checkoutRate get() = rate(beginCheckout, addToCart)
    val purchaseRate get() = rate(purchase, beginCheckout)
    private fun rate(n: Long, d: Long) = if (d <= 0) 0.0 else n.toDouble() / d
}
