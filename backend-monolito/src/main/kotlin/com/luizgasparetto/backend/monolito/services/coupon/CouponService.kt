package com.luizgasparetto.backend.monolito.services.coupon

import com.luizgasparetto.backend.monolito.dto.card.CardCartItemDto
import com.luizgasparetto.backend.monolito.exceptions.CouponValidationException
import com.luizgasparetto.backend.monolito.models.coupon.Coupon
import com.luizgasparetto.backend.monolito.models.coupon.DiscountType
import com.luizgasparetto.backend.monolito.repositories.CouponRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional
class CouponService(
    private val couponRepository: CouponRepository
) {
    private val log = LoggerFactory.getLogger(CouponService::class.java)

    data class CouponValidationResult(
        val valid: Boolean,
        val coupon: Coupon? = null,
        val discountAmount: BigDecimal = BigDecimal.ZERO,
        val finalTotal: BigDecimal = BigDecimal.ZERO,
        val errorMessage: String? = null
    )

    data class CouponValidationRequest(
        val code: String,
        val orderTotal: BigDecimal,
        val userEmail: String? = null,
        val cartItems: List<CardCartItemDto>? = null
    )

    fun validateCoupon(request: CouponValidationRequest): CouponValidationResult {
        log.info("Validating coupon: code={}, orderTotal={}", request.code, request.orderTotal)

        // Buscar cupom no banco (aceita variações case-insensitive)
        // O cupom LANCAMENTO funciona igual ao BONUS - sem validação especial de itens
        val coupon = couponRepository.findActiveByCodeIgnoreCase(request.code)
            .orElse(null) ?: return CouponValidationResult(
                valid = false,
                errorMessage = "Cupom não encontrado ou inativo"
            )

        try {
            // Validações básicas
            if (!coupon.isCurrentlyValid()) {
                return CouponValidationResult(
                    valid = false,
                    errorMessage = "Cupom expirado ou fora do período de validade"
                )
            }

            if (request.orderTotal < coupon.minimumOrderValue) {
                return CouponValidationResult(
                    valid = false,
                    errorMessage = "Valor mínimo do pedido não atingido. Mínimo: R$ ${coupon.minimumOrderValue}"
                )
            }

            // Verificar limite de uso total
            val usageLimit = coupon.usageLimit
            if (usageLimit != null) {
                val totalUsage = couponRepository.countUsageByCouponId(coupon.id!!)
                if (totalUsage >= usageLimit) {
                    return CouponValidationResult(
                        valid = false,
                        errorMessage = "Cupom esgotado"
                    )
                }
            }

            // Verificar limite de uso por usuário
            val usageLimitPerUser = coupon.usageLimitPerUser
            if (usageLimitPerUser != null && request.userEmail != null) {
                val userUsage = couponRepository.countUsageByCouponIdAndEmail(coupon.id!!, request.userEmail)
                if (userUsage >= usageLimitPerUser) {
                    return CouponValidationResult(
                        valid = false,
                        errorMessage = "Limite de uso por usuário atingido"
                    )
                }
            }

            // Calcular desconto
            val discountAmount = coupon.calculateDiscount(request.orderTotal)
            val finalTotal = request.orderTotal - discountAmount

            // Validação de segurança: garantir que o total final seja sempre >= 0.01
            if (finalTotal < BigDecimal("0.01")) {
                return CouponValidationResult(
                    valid = false,
                    errorMessage = "Desconto muito alto. Valor mínimo do pedido deve ser R$ 0,01"
                )
            }

            log.info("Coupon validated successfully: code={}, discount={}, finalTotal={}", 
                request.code, discountAmount, finalTotal)

            return CouponValidationResult(
                valid = true,
                coupon = coupon,
                discountAmount = discountAmount,
                finalTotal = finalTotal
            )

        } catch (e: Exception) {
            log.error("Error validating coupon: code={}", request.code, e)
            return CouponValidationResult(
                valid = false,
                errorMessage = "Erro interno ao validar cupom"
            )
        }
    }

    fun getCouponByCode(code: String): Coupon? {
        return couponRepository.findActiveByCodeIgnoreCase(code).orElse(null)
    }

    fun createCoupon(
        code: String,
        name: String,
        description: String?,
        discountType: DiscountType,
        discountValue: BigDecimal,
        minimumOrderValue: BigDecimal = BigDecimal.ZERO,
        maximumDiscountValue: BigDecimal? = null,
        usageLimit: Int? = null,
        usageLimitPerUser: Int? = null,
        validFrom: java.time.OffsetDateTime = java.time.OffsetDateTime.now(),
        validUntil: java.time.OffsetDateTime? = null
    ): Coupon {
        log.info("Creating coupon: code={}, type={}, value={}", code, discountType, discountValue)

        if (couponRepository.existsByCode(code)) {
            throw CouponValidationException("Cupom com código '$code' já existe")
        }

        val coupon = Coupon(
            code = code,
            name = name,
            description = description,
            discountType = discountType,
            discountValue = discountValue,
            minimumOrderValue = minimumOrderValue,
            maximumDiscountValue = maximumDiscountValue,
            usageLimit = usageLimit,
            usageLimitPerUser = usageLimitPerUser,
            validFrom = validFrom,
            validUntil = validUntil,
            active = true
        )

        return couponRepository.save(coupon)
    }

}
