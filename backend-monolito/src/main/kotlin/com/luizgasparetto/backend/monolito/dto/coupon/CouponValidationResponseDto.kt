package com.luizgasparetto.backend.monolito.dto.coupon

import com.luizgasparetto.backend.monolito.models.coupon.DiscountType
import java.math.BigDecimal

data class CouponValidationResponseDto(
    val valid: Boolean,
    val coupon: CouponDto? = null,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val finalTotal: BigDecimal = BigDecimal.ZERO,
    val errorMessage: String? = null
)

data class CouponDto(
    val id: Long?,
    val code: String,
    val name: String,
    val description: String?,
    val discountType: DiscountType,
    val discountValue: BigDecimal,
    val minimumOrderValue: BigDecimal,
    val maximumDiscountValue: BigDecimal?
)
