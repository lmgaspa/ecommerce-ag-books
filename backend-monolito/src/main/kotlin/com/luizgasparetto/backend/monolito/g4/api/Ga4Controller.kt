package com.luizgasparetto.backend.monolito.g4.api

import com.luizgasparetto.backend.monolito.g4.security.CurrentAuthor
import com.luizgasparetto.backend.monolito.ga4.dto.FunnelDTO
import com.luizgasparetto.backend.monolito.ga4.service.Ga4Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/ga4")
class Ga4Controller(
    private val ga4: Ga4Service,
    private val currentAuthor: CurrentAuthor // extrai UUID do JWT
) {
    @GetMapping("/funnel")
    fun funnel(@RequestParam from: String, @RequestParam to: String): FunnelDTO {
        val authorId = currentAuthor.id()           // UUID do sub
        return ga4.funnel(LocalDate.parse(from), LocalDate.parse(to), authorId)
    }
}
