package com.luizgasparetto.backend.monolito.g4.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CurrentAuthor {
    fun id(): UUID {
        val auth = SecurityContextHolder.getContext().authentication
        // adapte conforme seu principal/claims
        val sub = auth.name   // ou extraia do JwtAuthenticationToken.claims["sub"]
        return UUID.fromString(sub)
    }
}
