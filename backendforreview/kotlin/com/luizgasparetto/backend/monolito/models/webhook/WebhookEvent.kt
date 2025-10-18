package com.luizgasparetto.backend.monolito.models.webhook

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(
    name = "webhook_events",
    indexes = [
        Index(name = "idx_webhook_txid", columnList = "txid"),
        Index(name = "idx_webhook_charge_id", columnList = "chargeId"),
        Index(name = "idx_webhook_provider", columnList = "provider")
    ]
)
data class WebhookEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(length = 40)
    var txid: String? = null,

    @Column(length = 40)
    var status: String? = null,

    @Column(columnDefinition = "TEXT")
    var rawBody: String = "",

    @Column(length = 60)
    var chargeId: String? = null,

    @Column(length = 20)
    var provider: String? = null, // "PIX" ou "CARD" (opcional, ajuda em filtros)

    var receivedAt: OffsetDateTime = OffsetDateTime.now()
)