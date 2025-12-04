package com.luizgasparetto.backend.monolito.services.card

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class CardSecurityInvalidator(
    private val orderRepository: OrderRepository,
    private val cardService: CardService
) {
    private val log = LoggerFactory.getLogger(CardSecurityInvalidator::class.java)

    @Scheduled(fixedDelayString = "300000") // A cada 5 minutos
    @Transactional
    fun invalidateExpiredCards() {
        val now = OffsetDateTime.now()
        // Busca pedidos que estão WAITING e cuja reserva expirou
        val expiredOrders = orderRepository.findExpiredReservations(now, OrderStatus.WAITING)

        if (expiredOrders.isEmpty()) {
            log.debug("CARD_INVALIDATOR: Nenhuma reserva CARD expirada para invalidar.")
            return
        }

        log.info("CARD_INVALIDATOR: Encontradas {} reservas CARD expiradas para invalidar.", expiredOrders.size)

        var processed = 0
        expiredOrders.forEach { order ->
            // DEBUG: Log dos valores reais para identificar o problema
            log.debug("CARD_INVALIDATOR: Analisando pedido orderId={}, paymentMethod='{}', chargeId='{}'", 
                order.id, order.paymentMethod, order.chargeId)
            
            // Só processa CARD (com chargeId)
            val isCard = !order.chargeId.isNullOrBlank() && 
                        order.paymentMethod.equals("card", ignoreCase = true)
            
            log.debug("CARD_INVALIDATOR: isCard={} para orderId={}", isCard, order.id)
            
            if (!isCard) return@forEach

            processed++ // Conta apenas os processados

            if (order.chargeId != null) {
                runCatching {
                    // Tenta cancelar a cobrança na Efí
                    val cancelled = cardService.cancelCharge(order.chargeId!!)
                    if (cancelled) {
                        log.info("CARD_INVALIDATOR: Cobrança CARD chargeId={} cancelada ou já estava inativa na Efí.", order.chargeId)
                    } else {
                        log.warn("CARD_INVALIDATOR: Falha ao cancelar cobrança CARD chargeId={} na Efí (resposta não-2xx).", order.chargeId)
                    }
                }.onFailure { e ->
                    // Se chegar aqui, é um erro inesperado (não deveria acontecer após a correção do CardClient)
                    log.warn("CARD_INVALIDATOR: Erro inesperado ao cancelar CARD chargeId={}: {}", order.chargeId, e.message)
                }

                // Marca o pedido como EXPIRED no sistema
                order.status = OrderStatus.EXPIRED
                order.reserveExpiresAt = null // Limpa a data de expiração da reserva
                orderRepository.save(order)
                log.info("CARD_INVALIDATOR: Pedido orderId={} (chargeId={}) marcado como EXPIRED por segurança.", order.id, order.chargeId)
            } else {
                log.warn("CARD_INVALIDATOR: Pedido orderId={} em WAITING sem chargeId para invalidar.", order.id)
            }
        }
        log.info("CARD_INVALIDATOR: Concluída invalidação de {} reservas CARD expiradas (encontrados={}, processados={}).", processed, expiredOrders.size, processed)
    }
}