package com.luizgasparetto.backend.monolito.models.payout

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "payout_email",
    indexes = [
        Index(name = "idx_payout_email_payout_id", columnList = "payout_id"),
        Index(name = "idx_payout_email_order_id", columnList = "order_id"),
        Index(name = "idx_payout_email_status", columnList = "status"),
        Index(name = "idx_payout_email_sent_at", columnList = "sent_at"),
        Index(name = "idx_payout_email_type", columnList = "email_type")
    ]
)
data class PayoutEmail(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "payout_id", nullable = true)
    var payoutId: Long? = null,  // Opcional: pode não existir ainda (e-mail agendado)

    @Column(name = "order_id", nullable = true)
    var orderId: Long? = null,  // Sempre preenchido no código (nullable apenas para compatibilidade com registros antigos)

    @Column(name = "to_email", nullable = false, length = 255)
    var toEmail: String,

    @Column(name = "email_type", nullable = false, length = 40)
    var emailType: String,  // 'REPASSE_PIX', 'REPASSE_CARD', etc.

    @Column(name = "sent_at", nullable = false)
    var sentAt: OffsetDateTime = OffsetDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PayoutEmailStatus = PayoutEmailStatus.SENT,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null
)

enum class PayoutEmailStatus {
    SENT,
    FAILED
}

enum class PayoutEmailType {
    REPASSE_PIX,
    REPASSE_CARD
}

