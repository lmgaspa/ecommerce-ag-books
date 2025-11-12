package com.luizgasparetto.backend.monolito.services.author

import com.luizgasparetto.backend.monolito.dto.author.AuthorDTO
import com.luizgasparetto.backend.monolito.models.author.Author
import com.luizgasparetto.backend.monolito.repositories.AuthorRepository
import org.springframework.stereotype.Service

@Service
class AuthorService(
    private val authorRepository: AuthorRepository
) {

    private fun toDto(a: Author): AuthorDTO =
        AuthorDTO(
            id = a.id ?: error("Author sem ID persistido"),
            name = a.name,
            email = a.email
        )

    fun list(): List<AuthorDTO> =
        authorRepository.findAll().map { toDto(it) }

    fun getById(id: Long): AuthorDTO =
        toDto(authorRepository.findById(id).orElseThrow { NoSuchElementException("Author $id não encontrado") })

    fun getByEmail(email: String): AuthorDTO =
        toDto(authorRepository.findByEmail(email) ?: throw NoSuchElementException("Author $email não encontrado"))

    fun upsert(name: String, email: String): AuthorDTO {
        val existing = authorRepository.findByEmail(email)
        return if (existing != null) {
            if (existing.name != name) {
                existing.name = name
                toDto(authorRepository.save(existing))
            } else {
                toDto(existing)
            }
        } else {
            val saved = authorRepository.save(Author(name = name, email = email))
            toDto(saved)
        }
    }
}
