package com.luizgasparetto.backend.monolito.payment.services

import com.luizgasparetto.backend.monolito.payment.ports.OrderReadPort
import com.luizgasparetto.backend.monolito.payment.ports.OrderView
import com.luizgasparetto.backend.monolito.payment.ports.PaymentAuthorAccountPort
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutRepository
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutStatus

@Service
class PaymentTriggerService(
    private val orders: OrderReadPort,                    // porta/adapter que lê pedido + itens
    private val authors: PaymentAuthorAccountPort,       // porta p/ buscar pixKey por authorId
    private val orchestrator: PaymentPayoutOrchestrator,
    private val payoutRepo: PaymentPayoutRepository
) {
    /**
     * Dispara o processamento a partir de um webhook (orderRef = id do pedido em BIGINT).
     */
    fun tryTriggerByRef(orderRef: String?, externalId: String, provider: String) {
        val orderView = orders.findByOrderRefOrExternal(provider, orderRef, externalId) ?: return
        processOrder(orderView)
    }

    /**
     * Dispara o processamento a partir de um orderId conhecido.
     */
    fun tryTriggerByOrderId(orderId: Long) {
        val orderView = orders.findByOrderRefOrExternal(provider = "PIX", orderRef = orderId.toString(), externalId = "") ?: return
        processOrder(orderView)
    }

    private fun processOrder(order: OrderView) {
        // agrupa valor por autor
        val byAuthor: Map<Long, BigDecimal> = order.items
            .filter { it.authorId != 0L } // só itens mapeados
            .groupBy { it.authorId }
            .mapValues { (_, items) ->
                items.fold(BigDecimal.ZERO) { acc, it ->
                    acc + it.unitPrice.multiply(BigDecimal(it.quantity))
                }.setScale(2, RoundingMode.HALF_UP)
            }

        byAuthor.forEach { (authorId, amount) ->
            val pixKey = authors.findPixKeyByAuthorId(authorId) ?: return@forEach
            // evita duplicar payout já enviado/confirmado
            val existing = payoutRepo.findByOrderIdAndAuthorId(order.id, authorId)
            if (existing == null || existing.status !in setOf(PaymentPayoutStatus.SENT, PaymentPayoutStatus.CONFIRMED)) {
                orchestrator.createAndSendIfAbsent(
                    orderId = order.id,                // Long
                    authorId = authorId,               // Long
                    amount = amount,
                    pixKey = pixKey
                )
            }
        }
    }
}