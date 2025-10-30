package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.coupon.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CouponRepository : JpaRepository<Coupon, Long> {
    fun findByCode(code: String): Optional<Coupon>
    fun existsByCode(code: String): Boolean
    
    @Query("SELECT c FROM Coupon c WHERE c.code = :code AND c.active = true")
    fun findActiveByCode(@Param("code") code: String): Optional<Coupon>
    
    @Query("""
        SELECT COUNT(oc) FROM OrderCoupon oc 
        WHERE oc.coupon.id = :couponId
    """)
    fun countUsageByCouponId(@Param("couponId") couponId: Long): Long
    
    @Query("""
        SELECT COUNT(oc) FROM OrderCoupon oc 
        JOIN oc.order o 
        WHERE oc.coupon.id = :couponId 
        AND o.email = :email
    """)
    fun countUsageByCouponIdAndEmail(
        @Param("couponId") couponId: Long, 
        @Param("email") email: String
    ): Long
}
