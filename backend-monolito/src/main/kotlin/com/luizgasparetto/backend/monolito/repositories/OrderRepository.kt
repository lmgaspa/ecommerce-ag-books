package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.order.Order
import com.luizgasparetto.backend.monolito.models.order.OrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
interface OrderRepository : JpaRepository<Order, Long> {

    fun findByTxid(txid: String): Order?

    fun findByChargeId(chargeId: String): Order?   // ← usado no CardEfiWebhookService

    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsByTxid(txid: String): Order?

    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsByChargeId(chargeId: String): Order?

    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsById(id: Long): Order?

    @EntityGraph(attributePaths = ["items"])
    @Query(
        """
        select distinct o
          from #{#entityName} o
          left join o.items it
         where o.paid = false
           and o.status = :status
           and o.reserveExpiresAt is not null
           and o.reserveExpiresAt < :now
        """
    )
    fun findExpiredReservations(now: OffsetDateTime, status: OrderStatus): List<Order>
}
