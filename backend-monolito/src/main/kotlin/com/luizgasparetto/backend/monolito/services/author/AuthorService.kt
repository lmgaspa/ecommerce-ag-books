package com.luizgasparetto.backend.monolito.services.author

import com.luizgasparetto.backend.monolito.dto.author.AuthorDTO
import com.luizgasparetto.backend.monolito.dto.author.AuthorUpsertDTO
import com.luizgasparetto.backend.monolito.models.author.Author
import com.luizgasparetto.backend.monolito.repositories.AuthorRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthorService(
    private val authorRepository: AuthorRepository
) {
    private fun toDto(a: Author) = AuthorDTO(
        id = a.id ?: error("Author sem ID persistido"),
        name = a.name,
        email = a.email
    )

    fun list(): List<AuthorDTO> = authorRepository.findAll().map(::toDto)

    fun getById(id: Long): AuthorDTO =
        toDto(authorRepository.findById(id).orElseThrow { NoSuchElementException("Author $id não encontrado") })

    fun getByEmail(email: String): AuthorDTO =
        toDto(authorRepository.findByEmailIgnoreCase(email)
            ?: throw NoSuchElementException("Author $email não encontrado"))

    @Transactional
    fun create(body: AuthorUpsertDTO): AuthorDTO {
        if (authorRepository.existsByEmailIgnoreCase(body.email))
            throw IllegalArgumentException("Já existe author com email ${body.email}")
        return toDto(authorRepository.save(Author(name = body.name, email = body.email)))
    }

    @Transactional
    fun update(id: Long, body: AuthorUpsertDTO): AuthorDTO {
        val entity = authorRepository.findById(id)
            .orElseThrow { NoSuchElementException("Author $id não encontrado") }

        // contrato: email não muda aqui
        if (!entity.email.equals(body.email, ignoreCase = true))
            throw IllegalArgumentException("Email não pode ser alterado por este endpoint")

        if (entity.name != body.name) entity.name = body.name
        return toDto(entity)
    }

    /** Idempotente por email — ideal para o bootstrap. */
    @Transactional
    fun upsertByEmail(body: AuthorUpsertDTO): AuthorDTO {
        val existing = authorRepository.findByEmailIgnoreCase(body.email)
        return if (existing != null) {
            if (existing.name != body.name) existing.name = body.name
            toDto(existing)
        } else {
            toDto(authorRepository.save(Author(name = body.name, email = body.email)))
        }
    }
}
