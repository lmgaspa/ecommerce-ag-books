package com.luizgasparetto.backend.monolito.services.email.order

import com.luizgasparetto.backend.monolito.models.order.OrderEmail
import com.luizgasparetto.backend.monolito.models.order.OrderEmailStatus
import com.luizgasparetto.backend.monolito.models.order.OrderEmailType
import com.luizgasparetto.backend.monolito.repositories.OrderEmailRepository
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/**
 * Helper para persistir emails de status de pedido
 */
object OrderEmailHelper {
    private val log = LoggerFactory.getLogger(OrderEmailHelper::class.java)

    fun persistEmail(
        repository: OrderEmailRepository,
        orderId: Long,
        to: String,
        emailType: OrderEmailType,
        status: OrderEmailStatus,
        errorMessage: String? = null
    ) {
        try {
            val orderEmail = OrderEmail(
                orderId = orderId,
                toEmail = to,
                emailType = emailType.name,
                sentAt = OffsetDateTime.now(),
                status = status,
                errorMessage = errorMessage
            )

            repository.save(orderEmail)

            log.debug(
                "OrderEmail: persistido orderId={} type={} status={} error={}",
                orderId, emailType, status, errorMessage
            )
        } catch (e: Exception) {
            log.error("OrderEmail: erro ao persistir e-mail para orderId={}: {}", orderId, e.message, e)
        }
    }
}

