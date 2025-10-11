package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.payout.Payout
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PayoutRepository : JpaRepository<Payout, Long>
