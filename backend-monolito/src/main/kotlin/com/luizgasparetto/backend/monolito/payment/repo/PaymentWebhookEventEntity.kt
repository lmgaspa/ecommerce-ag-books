package com.luizgasparetto.backend.monolito.payment.repo

import jakarta.persistence.*
import com.fasterxml.jackson.databind.JsonNode
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "payment_webhook_events",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider","external_id","event_type"])]
)
open class PaymentWebhookEventEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null,               // <<<<< era UUID, agora Long

    @Column(nullable = false, length = 20)
    open var provider: String = "",

    @Column(name = "event_type", nullable = false, length = 60)
    open var eventType: String = "",

    @Column(name = "external_id", nullable = false, length = 80)
    open var externalId: String = "",

    @Column(name = "order_ref", length = 80)
    open var orderRef: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    open var payload: JsonNode? = null,

    @Column(name = "received_at", nullable = false)
    open var receivedAt: Instant = Instant.now()
)
