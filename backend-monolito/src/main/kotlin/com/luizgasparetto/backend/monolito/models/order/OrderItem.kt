package com.luizgasparetto.backend.monolito.models.order

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "order_items")
data class OrderItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 36)
    val bookId: String,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = true)
    val imageUrl: String? = null,

    @Column(nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
)