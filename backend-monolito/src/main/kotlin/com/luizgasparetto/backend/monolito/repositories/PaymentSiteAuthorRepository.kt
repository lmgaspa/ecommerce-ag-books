package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.payment.PaymentSiteAuthor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PaymentSiteAuthorRepository : JpaRepository<PaymentSiteAuthor, Long> {
    
    @Query("SELECT a FROM PaymentSiteAuthor a WHERE a.active = true")
    fun findActiveAuthor(): PaymentSiteAuthor?
    
    @Query("SELECT a FROM PaymentSiteAuthor a WHERE a.active = true AND a.pixKey IS NOT NULL AND a.pixKey != ''")
    fun findActiveAuthorWithPixKey(): PaymentSiteAuthor?
}
