package com.luizgasparetto.backend.monolito.services.payment

import com.luizgasparetto.backend.monolito.dto.payment.PaymentSiteAuthorCreateRequest
import com.luizgasparetto.backend.monolito.dto.payment.PaymentSiteAuthorDTO
import com.luizgasparetto.backend.monolito.dto.payment.PaymentSiteAuthorUpdateRequest
import com.luizgasparetto.backend.monolito.models.payment.PaymentSiteAuthor
import com.luizgasparetto.backend.monolito.repositories.PaymentSiteAuthorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PaymentSiteAuthorService(
    private val repository: PaymentSiteAuthorRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getActiveAuthor(): PaymentSiteAuthorDTO? {
        val author = repository.findActiveAuthor()
        return author?.toDTO()
    }

    fun getActiveAuthorWithPixKey(): PaymentSiteAuthorDTO? {
        val author = repository.findActiveAuthorWithPixKey()
        return author?.toDTO()
    }

    @Transactional
    fun createAuthor(request: PaymentSiteAuthorCreateRequest): PaymentSiteAuthorDTO {
        // Desativa todos os autores existentes se o novo for ativo
        if (request.active) {
            repository.findAll().forEach { author ->
                if (author.active) {
                    repository.save(author.copy(active = false, updatedAt = OffsetDateTime.now()))
                }
            }
        }

        val author = PaymentSiteAuthor(
            name = request.name,
            email = request.email,
            pixKey = request.pixKey,
            active = request.active
        )

        val saved = repository.save(author)
        log.info("Autor criado: id={}, name={}, active={}", saved.id, saved.name, saved.active)
        return saved.toDTO()
    }

    @Transactional
    fun updateAuthor(id: Long, request: PaymentSiteAuthorUpdateRequest): PaymentSiteAuthorDTO {
        val author = repository.findById(id).orElseThrow { 
            IllegalArgumentException("Autor não encontrado com id: $id") 
        }

        // Se está ativando este autor, desativa todos os outros
        if (request.active == true) {
            repository.findAll().forEach { otherAuthor ->
                if (otherAuthor.id != id && otherAuthor.active) {
                    repository.save(otherAuthor.copy(active = false, updatedAt = OffsetDateTime.now()))
                }
            }
        }

        val updated = author.copy(
            name = request.name ?: author.name,
            email = request.email ?: author.email,
            pixKey = request.pixKey ?: author.pixKey,
            active = request.active ?: author.active,
            updatedAt = OffsetDateTime.now()
        )

        val saved = repository.save(updated)
        log.info("Autor atualizado: id={}, name={}, active={}", saved.id, saved.name, saved.active)
        return saved.toDTO()
    }

    @Transactional
    fun activateAuthor(id: Long): PaymentSiteAuthorDTO {
        val author = repository.findById(id).orElseThrow { 
            IllegalArgumentException("Autor não encontrado com id: $id") 
        }

        // Desativa todos os outros autores
        repository.findAll().forEach { otherAuthor ->
            if (otherAuthor.id != id && otherAuthor.active) {
                repository.save(otherAuthor.copy(active = false, updatedAt = OffsetDateTime.now()))
            }
        }

        val activated = author.copy(active = true, updatedAt = OffsetDateTime.now())
        val saved = repository.save(activated)
        log.info("Autor ativado: id={}, name={}", saved.id, saved.name)
        return saved.toDTO()
    }

    fun getAllAuthors(): List<PaymentSiteAuthorDTO> {
        return repository.findAll().map { it.toDTO() }
    }

    private fun PaymentSiteAuthor.toDTO(): PaymentSiteAuthorDTO {
        return PaymentSiteAuthorDTO(
            id = this.id,
            name = this.name,
            email = this.email,
            pixKey = this.pixKey,
            active = this.active
        )
    }
}
