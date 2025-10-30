package com.luizgasparetto.backend.monolito.services.pix

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class PixSecurityInvalidator(
    private val orderRepository: OrderRepository,
    private val pixClient: PixClient
) {
    private val log = LoggerFactory.getLogger(PixSecurityInvalidator::class.java)

    /**
     * Invalida PIX por questões de segurança quando expira.
     * Executa a cada 5 minutos para limpar PIX órfãos.
     */
    @Scheduled(fixedDelayString = "300000") // 5 minutos
    @Transactional
    fun invalidateExpiredPix() {
        val now = OffsetDateTime.now()
        val expiredOrders = orderRepository.findExpiredReservations(now, OrderStatus.WAITING)
        
        if (expiredOrders.isEmpty()) {
            log.debug("PIX-SECURITY: nenhum PIX expirado para invalidar")
            return
        }

        var invalidated = 0
        var processed = 0
        expiredOrders.forEach { order ->
            // Só processa PIX (sem chargeId)
            val isPix = order.chargeId.isNullOrBlank() && 
                       order.paymentMethod.equals("pix", ignoreCase = true)
            
            if (!isPix) return@forEach

            processed++ // Conta apenas os processados

            if (order.txid != null) {
                // Cancela PIX na Efí por segurança
                runCatching {
                    val cancelled = pixClient.cancel(order.txid!!)
                    if (cancelled) {
                        log.info("PIX-SECURITY: PIX invalidado por segurança txid={}", order.txid)
                        invalidated++
                    } else {
                        log.warn("PIX-SECURITY: falha ao cancelar PIX txid={}", order.txid)
                    }
                }.onFailure { e ->
                    log.error("PIX-SECURITY: erro ao cancelar PIX txid={}: {}", order.txid, e.message)
                }
            }

            // Marca como expirado
            order.status = OrderStatus.EXPIRED
            order.reserveExpiresAt = null
            orderRepository.save(order)
        }

        log.info("PIX-SECURITY: encontrados={}, processados={}, invalidados={}", expiredOrders.size, processed, invalidated)
    }
}
