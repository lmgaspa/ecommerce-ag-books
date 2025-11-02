package com.luizgasparetto.backend.monolito.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SecurityHeadersFilter : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        res.setHeader("X-Content-Type-Options", "nosniff")
        res.setHeader("X-Frame-Options", "DENY")
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin")
        res.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin")
        res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        res.setHeader(
            "Content-Security-Policy",
            "default-src 'self'; " +
                    "script-src 'self' https://www.googletagmanager.com https://www.google-analytics.com; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: https:; " +
                    "connect-src 'self' https://www.google-analytics.com https://region1.google-analytics.com; " +
                    "font-src 'self' data:; " +
                    "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
        )
        chain.doFilter(req, res)
    }
}
