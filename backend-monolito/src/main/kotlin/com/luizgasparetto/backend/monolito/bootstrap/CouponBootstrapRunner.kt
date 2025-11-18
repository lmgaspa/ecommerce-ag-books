package com.luizgasparetto.backend.monolito.bootstrap

import com.luizgasparetto.backend.monolito.config.coupon.CouponProperties
import com.luizgasparetto.backend.monolito.models.coupon.DiscountType
import com.luizgasparetto.backend.monolito.repositories.CouponRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Component
class CouponBootstrapRunner(
    private val couponProperties: CouponProperties,
    private val couponRepository: CouponRepository,
    private val entityManager: EntityManager
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: org.springframework.boot.ApplicationArguments) {
        if (!couponProperties.enabled) {
            log.info("[bootstrap.coupon.bonus] disabled — skipping")
            return
        }

        val code = couponProperties.code?.uppercase()?.trim() ?: run {
            log.warn("[bootstrap.coupon.bonus] enabled but COUPON_BONUS_CODE env var is missing — skipping")
            return
        }
        
        if (code.isBlank()) {
            log.warn("[bootstrap.coupon.bonus] enabled but code is blank — skipping")
            return
        }

        val discountValue = couponProperties.discountValue ?: run {
            log.warn("[bootstrap.coupon.bonus] enabled but COUPON_BONUS_DISCOUNT_VALUE env var is missing — skipping")
            return
        }

        log.info("[bootstrap.coupon.bonus] START code='{}' discountValue={}", code, discountValue)

        val existing = couponRepository.findByCodeIgnoreCase(code).orElse(null)

        val validFrom = couponProperties.validFrom?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } ?: OffsetDateTime.now()

        val validUntil = couponProperties.validUntil?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }

        if (existing != null) {
            // Atualizar cupom existente via query nativa (idempotente)
            log.info("[bootstrap.coupon.bonus] cupom '{}' já existe (id={}) — atualizando", code, existing.id)
            
            entityManager.createNativeQuery("""
                UPDATE coupons 
                SET name = :name,
                    description = :description,
                    discount_type = :discountType,
                    discount_value = :discountValue,
                    minimum_order_value = :minimumOrderValue,
                    valid_from = :validFrom,
                    valid_until = :validUntil,
                    active = :active,
                    updated_at = NOW()
                WHERE UPPER(code) = UPPER(:code)
            """.trimIndent())
                .setParameter("code", code)
                .setParameter("name", couponProperties.name ?: "Cupom Bônus")
                .setParameter("description", couponProperties.description)
                .setParameter("discountType", DiscountType.FIXED.name)
                .setParameter("discountValue", discountValue)
                .setParameter("minimumOrderValue", couponProperties.minimumOrderValue)
                .setParameter("validFrom", validFrom)
                .setParameter("validUntil", validUntil)
                .setParameter("active", couponProperties.active)
                .executeUpdate()
            
            log.info("[bootstrap.coupon.bonus] DONE — updated coupon code={} discountValue={}", 
                code, discountValue)
        } else {
            // Criar novo cupom
            val coupon = com.luizgasparetto.backend.monolito.models.coupon.Coupon(
                code = code,
                name = couponProperties.name ?: "Cupom Bônus",
                description = couponProperties.description,
                discountType = DiscountType.FIXED,
                discountValue = discountValue,
                minimumOrderValue = couponProperties.minimumOrderValue,
                validFrom = validFrom,
                validUntil = validUntil,
                active = couponProperties.active
            )

            val saved = couponRepository.save(coupon)
            log.info("[bootstrap.coupon.bonus] DONE — created coupon id={} code={} discountValue={}", 
                saved.id, saved.code, saved.discountValue)
        }
    }
}

