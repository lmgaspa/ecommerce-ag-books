package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.payout.PayoutEmail
import com.luizgasparetto.backend.monolito.models.payout.PayoutEmailStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PayoutEmailRepository : JpaRepository<PayoutEmail, Long> {

    fun findByPayoutId(payoutId: Long): List<PayoutEmail>

    fun findByPayoutIdAndEmailType(payoutId: Long, emailType: String): List<PayoutEmail>

    fun findByOrderId(orderId: Long): List<PayoutEmail>

    fun findByOrderIdAndEmailType(orderId: Long, emailType: String): List<PayoutEmail>

    /**
     * Usado para idempotência de envio:
     * verifica se já existe e-mail desse payout + tipo + status.
     *
     * Mapeia para os campos:
     *  - payoutId: Long?
     *  - emailType: String
     *  - status:   PayoutEmailStatus (ENUM)
     */
    fun existsByPayoutIdAndEmailTypeAndStatus(
        payoutId: Long?,
        emailType: String,
        status: PayoutEmailStatus
    ): Boolean
}
