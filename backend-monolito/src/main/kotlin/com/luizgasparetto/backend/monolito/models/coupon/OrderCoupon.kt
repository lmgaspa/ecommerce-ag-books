package com.luizgasparetto.backend.monolito.models.coupon

import com.luizgasparetto.backend.monolito.models.order.Order
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(
    name = "order_coupons",
    indexes = [
        Index(name = "idx_order_coupons_order_id", columnList = "order_id"),
        Index(name = "idx_order_coupons_coupon_id", columnList = "coupon_id")
    ]
)
data class OrderCoupon(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: Order,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    val coupon: Coupon,

    @Column(nullable = false)
    val originalTotal: BigDecimal,

    @Column(nullable = false)
    val discountAmount: BigDecimal,

    @Column(nullable = false)
    val finalTotal: BigDecimal,

    @Column(nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
) {
    init {
        // Validação de integridade dos dados
        require(originalTotal >= BigDecimal.ZERO) { "Original total must be non-negative" }
        require(discountAmount >= BigDecimal.ZERO) { "Discount amount must be non-negative" }
        require(finalTotal >= BigDecimal.ZERO) { "Final total must be non-negative" }
        require(originalTotal - discountAmount == finalTotal) { 
            "Final total must equal original total minus discount amount" 
        }
    }
}
