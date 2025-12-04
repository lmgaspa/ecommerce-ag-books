package com.luizgasparetto.backend.monolito.models.order

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
        name = "order_email",
        indexes =
                [
                        Index(name = "idx_order_email_order_id", columnList = "order_id"),
                        Index(name = "idx_order_email_status", columnList = "status"),
                        Index(name = "idx_order_email_sent_at", columnList = "sent_at"),
                        Index(name = "idx_order_email_type", columnList = "email_type")]
)
class OrderEmail(
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
        @Column(name = "order_id", nullable = false) var orderId: Long,
        @Column(name = "to_email", nullable = false, length = 255) var toEmail: String,
        @Column(name = "email_type", nullable = false, length = 40)
        var emailType: String, // 'PENDING', 'PAID', 'FAILED', etc.
        @Column(name = "sent_at", nullable = false)
        var sentAt: OffsetDateTime = OffsetDateTime.now(),
        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false, length = 20)
        var status: OrderEmailStatus = OrderEmailStatus.SENT,
        @Column(name = "error_message", columnDefinition = "TEXT") var errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as OrderEmail
        return id != null && id == other.id
    }

    override fun hashCode(): Int =
            31 // Constant hash code for entities to avoid issues when ID changes

    override fun toString(): String {
        return "OrderEmail(id=$id, orderId=$orderId, toEmail='$toEmail', emailType='$emailType', status=$status)"
    }
}

enum class OrderEmailStatus {
    SENT,
    FAILED
}

enum class OrderEmailType {
    PENDING, // Pedido criado (aguardando pagamento)
    PAID, // Pagamento aprovado
    FAILED, // Pagamento rejeitado
    CANCELED, // Pedido cancelado
    REFUNDED, // Pedido estornado
    EXPIRED // Pedido expirado
}
