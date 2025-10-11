package com.luizgasparetto.backend.monolito.models.autopayout.record

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "auto_payouts")
data class PayoutRecord(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val idEnvio: String? = null,
    val endToEndId: String? = null,

    @Column(precision = 19, scale = 2)
    val target: BigDecimal,

    @Column(precision = 19, scale = 2)
    val margin: BigDecimal,

    @Column(precision = 19, scale = 2)
    val fee: BigDecimal,

    @Column(precision = 19, scale = 2)
    val sent: BigDecimal,

    val mode: String,              // NET | GROSS
    val favoredKey: String,

    val status: String? = null,    // REALIZADO/EM_PROCESSAMENTO/NAO_REALIZADO
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)