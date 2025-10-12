package com.luizgasparetto.backend.monolito.payment.repo

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "payment_payouts")
open class PaymentPayoutEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,            // << Long, NUNCA UUID

    @Column(name="author_id", nullable=false) open var authorId: Long = 0,
    @Column(name="order_id",  nullable=false) open var orderId: Long = 0,

    @Column(nullable = false, precision = 12, scale = 2)
    open var amount: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    open var status: PaymentPayoutStatus = PaymentPayoutStatus.CREATED,

    @Column(name = "pix_key", nullable = false, length = 140)
    open var pixKey: String = "",

    @Column(name = "efi_id_envio", unique = true)
    open var efiIdEnvio: String? = null,

    @Column(name = "fail_reason")
    open var failReason: String? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),

    @Column(name = "sent_at")
    open var sentAt: Instant? = null,

    @Column(name = "confirmed_at")
    open var confirmedAt: Instant? = null
)

enum class PaymentPayoutStatus { CREATED, SENT, CONFIRMED, FAILED, CANCELED }
