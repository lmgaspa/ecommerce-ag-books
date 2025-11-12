package com.luizgasparetto.backend.monolito.bootstrap

import com.luizgasparetto.backend.monolito.dto.author.AuthorUpsertDTO
import com.luizgasparetto.backend.monolito.services.author.AuthorService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class AuthorBootstrapRunner(
    private val authorService: AuthorService,
    @Value("\${bootstrap.author.enabled:false}") private val enabled: Boolean,
    @Value("\${bootstrap.author.name:}") private val name: String,
    @Value("\${bootstrap.author.email:}") private val email: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!enabled) {
            log.info("[bootstrap.author] disabled — skipping")
            return
        }
        if (name.isBlank() || email.isBlank()) {
            log.warn("[bootstrap.author] enabled but missing name/email — skipping")
            return
        }

        log.info("[bootstrap.author] START name='{}' email='{}'", name, email)
        val dto = AuthorUpsertDTO(name = name.trim(), email = email.trim())
        val ensured = authorService.upsertByEmail(dto)
        log.info("[bootstrap.author] DONE — ensured author id={} email={}", ensured.id, ensured.email)
    }
}
