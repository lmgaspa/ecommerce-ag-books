package com.luizgasparetto.backend.monolito.bootstrap

import com.luizgasparetto.backend.monolito.config.coupon.CouponLancamentoProperties
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
class CouponLancamentoBootstrapRunner(
    private val lancamentoProperties: CouponLancamentoProperties,
    private val couponRepository: CouponRepository,
    private val entityManager: EntityManager
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: org.springframework.boot.ApplicationArguments) {
        if (!lancamentoProperties.enabled) {
            log.info("[bootstrap.coupon.lancamento] disabled — skipping")
            return
        }

        val code = lancamentoProperties.code?.uppercase()?.trim() ?: run {
            log.warn("[bootstrap.coupon.lancamento] enabled but COUPON_LANCAMENTO_CODE env var is missing — skipping")
            return
        }
        
        if (code.isBlank()) {
            log.warn("[bootstrap.coupon.lancamento] enabled but code is blank — skipping")
            return
        }

        val discountValue = lancamentoProperties.discountValue ?: run {
            log.warn("[bootstrap.coupon.lancamento] enabled but COUPON_LANCAMENTO_DISCOUNT_VALUE env var is missing — skipping")
            return
        }

        log.info("[bootstrap.coupon.lancamento] START code='{}' discountValue={}", 
            code, discountValue)

        val existing = couponRepository.findByCodeIgnoreCase(code).orElse(null)

        val validFrom = lancamentoProperties.validFrom?.takeIf { it.isNotBlank() }?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } ?: OffsetDateTime.now()

        val validUntil = lancamentoProperties.validUntil?.takeIf { it.isNotBlank() }?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }

        if (existing != null) {
            // Atualizar cupom existente via query nativa (idempotente)
            log.info("[bootstrap.coupon.lancamento] cupom '{}' já existe (id={}) — atualizando", code, existing.id)
            
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
                .setParameter("name", lancamentoProperties.name ?: "Cupom Lançamento")
                .setParameter("description", lancamentoProperties.description ?: "Cupom especial para livros de lançamento")
                .setParameter("discountType", DiscountType.FIXED.name)
                .setParameter("discountValue", discountValue)
                .setParameter("minimumOrderValue", lancamentoProperties.minimumOrderValue)
                .setParameter("validFrom", validFrom)
                .setParameter("validUntil", validUntil)
                .setParameter("active", lancamentoProperties.active)
                .executeUpdate()
            
            log.info("[bootstrap.coupon.lancamento] DONE — updated coupon code={} discountValue={}", 
                code, discountValue)
        } else {
            // Criar novo cupom
            val coupon = com.luizgasparetto.backend.monolito.models.coupon.Coupon(
                code = code,
                name = lancamentoProperties.name ?: "Cupom Lançamento",
                description = lancamentoProperties.description ?: "Cupom especial para livros de lançamento",
                discountType = DiscountType.FIXED,
                discountValue = discountValue,
                minimumOrderValue = lancamentoProperties.minimumOrderValue,
                validFrom = validFrom,
                validUntil = validUntil,
                active = lancamentoProperties.active
            )

            val saved = couponRepository.save(coupon)
            log.info("[bootstrap.coupon.lancamento] DONE — created coupon id={} code={} discountValue={}", 
                saved.id, saved.code, saved.discountValue)
        }
    }
}

