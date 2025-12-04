package com.luizgasparetto.backend.monolito.controllers.coupon

import com.luizgasparetto.backend.monolito.dto.coupon.CouponDto
import com.luizgasparetto.backend.monolito.dto.coupon.CouponValidationRequestDto
import com.luizgasparetto.backend.monolito.dto.coupon.CouponValidationResponseDto
import com.luizgasparetto.backend.monolito.services.coupon.CouponService
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("${ApiRoutes.API_V1}/coupons")
class CouponController(
    private val couponService: CouponService
) {
    private val log = LoggerFactory.getLogger(CouponController::class.java)

    @PostMapping("/validate")
    fun validateCoupon(@Valid @RequestBody request: CouponValidationRequestDto): ResponseEntity<CouponValidationResponseDto> {
        log.info("Validating coupon: code={}", request.code)

        val result = couponService.validateCoupon(
            CouponService.CouponValidationRequest(
                code = request.code,
                orderTotal = request.orderTotal,
                userEmail = request.userEmail,
                cartItems = request.cartItems
            )
        )

        val response = CouponValidationResponseDto(
            valid = result.valid,
            coupon = result.coupon?.let { coupon ->
                CouponDto(
                    id = coupon.id,
                    code = coupon.code,
                    name = coupon.name,
                    description = coupon.description,
                    discountType = coupon.discountType,
                    discountValue = coupon.discountValue,
                    minimumOrderValue = coupon.minimumOrderValue,
                    maximumDiscountValue = coupon.maximumDiscountValue
                )
            },
            discountAmount = result.discountAmount,
            finalTotal = result.finalTotal,
            errorMessage = result.errorMessage
        )

        return if (result.valid) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @GetMapping("/{code}")
    fun getCouponInfo(@PathVariable code: String): ResponseEntity<CouponDto> {
        log.info("Getting coupon info: code={}", code)

        val coupon = couponService.getCouponByCode(code)
            ?: return ResponseEntity.notFound().build()

        val couponDto = CouponDto(
            id = coupon.id,
            code = coupon.code,
            name = coupon.name,
            description = coupon.description,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            minimumOrderValue = coupon.minimumOrderValue,
            maximumDiscountValue = coupon.maximumDiscountValue
        )

        return ResponseEntity.ok(couponDto)
    }
}
