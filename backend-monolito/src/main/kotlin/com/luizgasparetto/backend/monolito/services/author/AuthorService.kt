package com.luizgasparetto.backend.monolito.services.author

import com.luizgasparetto.backend.monolito.authors.AuthorRepository
import com.luizgasparetto.backend.monolito.dto.author.AuthorDTO
import com.luizgasparetto.backend.monolito.dto.author.AuthorUpsertDTO
import com.luizgasparetto.backend.monolito.models.author.Author
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class AuthorService(
    private val authorRepository: AuthorRepository
) {
    private fun toDto(a: Author) = AuthorDTO(
        id = a.id ?: error("Author sem ID persistido"),
        name = a.name,
        email = a.email
    )

    /** Busca case-insensitive (repo expõe Optional<Author>). */
    fun findByEmailCaseInsensitive(rawEmail: String): Optional<Author> {
        val email = rawEmail.trim()
        return authorRepository.findByEmailIgnoreCase(email)
    }

    /** Existe por e-mail (case-insensitive). */
    fun emailExists(rawEmail: String): Boolean {
        val email = rawEmail.trim()
        return authorRepository.existsByEmailIgnoreCase(email)
    }

    /** Lista todos como DTO. */
    fun list(): List<AuthorDTO> =
        authorRepository.findAll().map(::toDto)

    /** Busca por ID como DTO. */
    fun getById(id: Long): AuthorDTO =
        toDto(
            authorRepository.findById(id)
                .orElseThrow { NoSuchElementException("Author $id não encontrado") }
        )

    /** Busca por e-mail como DTO (case-insensitive). */
    fun getByEmail(rawEmail: String): AuthorDTO {
        val email = rawEmail.trim()
        val entity = authorRepository.findByEmailIgnoreCase(email)
            .orElseThrow { NoSuchElementException("Author $email não encontrado") }
        return toDto(entity)
    }

    /** Criação com validação de unicidade (banco garante por índice funcional). */
    @Transactional
    fun create(body: AuthorUpsertDTO): AuthorDTO {
        val email = body.email.trim()
        if (authorRepository.existsByEmailIgnoreCase(email)) {
            throw IllegalArgumentException("Já existe author com email $email")
        }
        val saved = authorRepository.save(Author(name = body.name, email = email))
        return toDto(saved)
    }

    /** Atualização do nome. O e-mail não pode ser alterado por este endpoint. */
    @Transactional
    fun update(id: Long, body: AuthorUpsertDTO): AuthorDTO {
        val email = body.email.trim()
        val entity = authorRepository.findById(id)
            .orElseThrow { NoSuchElementException("Author $id não encontrado") }

        if (!entity.email.equals(email, ignoreCase = true)) {
            throw IllegalArgumentException("Email não pode ser alterado por este endpoint")
        }

        if (entity.name != body.name) {
            entity.name = body.name
        }
        return toDto(entity) // JPA fará dirty checking
    }

    /** Idempotente por e-mail — ideal para bootstrap/seed. */
    @Transactional
    fun upsertByEmail(body: AuthorUpsertDTO): AuthorDTO {
        val email = body.email.trim()
        val opt = authorRepository.findByEmailIgnoreCase(email)
        return if (opt.isPresent) {
            val existing = opt.get()
            if (existing.name != body.name) {
                existing.name = body.name
            }
            toDto(existing)
        } else {
            toDto(authorRepository.save(Author(name = body.name, email = email)))
        }
    }
}
