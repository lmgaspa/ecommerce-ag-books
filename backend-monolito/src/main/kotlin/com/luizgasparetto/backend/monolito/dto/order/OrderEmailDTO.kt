package com.luizgasparetto.backend.monolito.dto.order

import com.luizgasparetto.backend.monolito.models.order.OrderEmail
import java.time.OffsetDateTime

data class OrderEmailDTO(
    val id: Long,
    val orderId: Long,
    val toEmail: String,
    val emailType: String,
    val sentAt: OffsetDateTime,
    val status: String,
    val errorMessage: String?
) {
    companion object {
        fun from(entity: OrderEmail): OrderEmailDTO = OrderEmailDTO(
            id = entity.id ?: throw IllegalStateException("OrderEmail sem ID"),
            orderId = entity.orderId,
            toEmail = entity.toEmail,
            emailType = entity.emailType,
            sentAt = entity.sentAt,
            status = entity.status.name,
            errorMessage = entity.errorMessage
        )
    }
}

