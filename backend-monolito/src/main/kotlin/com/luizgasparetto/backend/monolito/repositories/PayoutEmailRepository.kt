package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.payout.PayoutEmail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PayoutEmailRepository : JpaRepository<PayoutEmail, Long> {
    fun findByPayoutId(payoutId: Long): List<PayoutEmail>
    fun findByPayoutIdAndEmailType(payoutId: Long, emailType: String): List<PayoutEmail>
    fun findByOrderId(orderId: Long): List<PayoutEmail>
    fun findByOrderIdAndEmailType(orderId: Long, emailType: String): List<PayoutEmail>
}

