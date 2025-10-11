// src/main/kotlin/com/luizgasparetto/backend/monolito/models/payout/Payout.kt
package com.luizgasparetto.backend.monolito.models.payout

import jakarta.persistence.*

@Entity
@Table(name = "payouts")
class Payout(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var transferId: String? = null,
    var status: String? = null,
    var amount: String? = null
)
