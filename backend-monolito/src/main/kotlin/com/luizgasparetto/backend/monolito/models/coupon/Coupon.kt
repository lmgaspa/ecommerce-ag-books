package com.luizgasparetto.backend.monolito.models.coupon

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(
    name = "coupons",
    indexes = [
        Index(name = "idx_coupons_code", columnList = "code"),
        Index(name = "idx_coupons_active", columnList = "active"),
        Index(name = "idx_coupons_valid_dates", columnList = "valid_from, valid_until")
    ]
)
data class Coupon(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    val code: String,

    @Column(nullable = false, length = 200)
    val name: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val discountType: DiscountType,

    @Column(nullable = false)
    val discountValue: BigDecimal,

    @Column(nullable = false)
    val minimumOrderValue: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = true)
    val maximumDiscountValue: BigDecimal? = null,

    @Column(nullable = true)
    val usageLimit: Int? = null,

    @Column(nullable = true)
    val usageLimitPerUser: Int? = null,

    @Column(nullable = false)
    val validFrom: OffsetDateTime,

    @Column(nullable = true)
    val validUntil: OffsetDateTime? = null,

    @Column(nullable = false)
    val active: Boolean = true,

    @Column(nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(nullable = false)
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
) {
    fun isCurrentlyValid(): Boolean {
        val now = OffsetDateTime.now()
        return active && 
               now.isAfter(validFrom) && 
               (validUntil == null || now.isBefore(validUntil))
    }

    fun calculateDiscount(orderTotal: BigDecimal): BigDecimal {
        if (!isCurrentlyValid()) {
            return BigDecimal.ZERO
        }

        if (orderTotal < minimumOrderValue) {
            return BigDecimal.ZERO
        }

        val discount = when (discountType) {
            DiscountType.FIXED -> discountValue
            DiscountType.PERCENTAGE -> {
                val percentageDiscount = orderTotal.multiply(discountValue.divide(BigDecimal(100)))
                if (maximumDiscountValue != null) {
                    percentageDiscount.min(maximumDiscountValue)
                } else {
                    percentageDiscount
                }
            }
        }

        // O desconto não pode ser maior que o total do pedido
        // Garantir que sempre reste pelo menos R$ 0.01
        val maxAllowedDiscount = orderTotal - BigDecimal("0.01")
        return discount.min(maxAllowedDiscount)
    }
}

enum class DiscountType {
    FIXED,      // Valor fixo em reais
    PERCENTAGE  // Percentual de desconto
}
