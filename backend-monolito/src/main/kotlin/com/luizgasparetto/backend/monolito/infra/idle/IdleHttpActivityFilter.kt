package com.luizgasparetto.backend.monolito.infra.idle

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(5)
class IdleHttpActivityFilter(
    private val idle: IdleStateService
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val woke = idle.wakeIfConfigured()
        idle.markActivity()
        // Optionally add header to observe state transitions
        if (woke) {
            response.addHeader("X-Idle-Mode", "woke")
        }
        filterChain.doFilter(request, response)
    }
}


