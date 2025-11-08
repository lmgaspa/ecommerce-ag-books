package com.seuapp.ga4.service

import com.seuapp.ga4.dto.FunnelDTO
import java.time.LocalDate
import java.util.UUID

interface Ga4Service {
    fun funnel(from: LocalDate, to: LocalDate, authorId: UUID): FunnelDTO
}
