package com.luizgasparetto.backend.monolito.models.payment

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "payment_site_author")
data class PaymentSiteAuthor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column
    val email: String? = null,

    @Column(name = "pix_key", nullable = false)
    val pixKey: String,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
