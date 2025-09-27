package com.luizgasparetto.backend.monolito.jobs

import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.services.book.BookService
import com.luizgasparetto.backend.monolito.services.card.CardService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class CardReservationReaper(
    private val orderRepository: OrderRepository,
    private val bookService: BookService,
    private val cardService: CardService
) {
    private val log = LoggerFactory.getLogger(CardReservationReaper::class.java)

    @Scheduled(fixedDelayString = "\${checkout.reserve.reaper-ms:60000}")
    @Transactional
    fun reap() {
        val now = OffsetDateTime.now()
        val expired = orderRepository.findExpiredReservations(now, OrderStatus.WAITING)
        if (expired.isEmpty()) return

        var released = 0
        for (order in expired) {
            val chargeId = order.chargeId
            // só processa reservas de CARTÃO
            if (chargeId.isNullOrBlank()) continue

            // 1) devolve estoque
            for (item in order.items) {
                bookService.release(item.bookId, item.quantity)
                released += item.quantity
            }

            // 2) tenta cancelar a cobrança no provedor do cartão
            try {
                val ok = cardService.cancelCharge(chargeId)
                if (ok) log.info("CARD-REAPER: cobrança cancelada chargeId={}", chargeId)
                else     log.warn("CARD-REAPER: cancel não-2xx chargeId={}", chargeId)
            } catch (e: Exception) {
                log.warn("CARD-REAPER: falha ao cancelar chargeId={}: {}", chargeId, e.message)
            }

            // 3) marca pedido como expirado
            order.status = OrderStatus.EXPIRED
            order.reserveExpiresAt = null
            orderRepository.save(order)
            log.info("CARD-REAPER: reserva expirada orderId={} liberada", order.id)
        }

        log.info("CARD-REAPER: pedidos expirados processados={}, unidades devolvidas={}", expired.size, released)
    }
}
