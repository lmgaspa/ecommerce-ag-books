package com.luizgasparetto.backend.monolito.payment.repo

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentPayoutRepository : JpaRepository<PaymentPayoutEntity, Long> {
    fun findByOrderIdAndAuthorId(orderId: Long, authorId: Long): PaymentPayoutEntity?
}

interface PaymentWebhookEventRepository : JpaRepository<PaymentWebhookEventEntity, Long>
