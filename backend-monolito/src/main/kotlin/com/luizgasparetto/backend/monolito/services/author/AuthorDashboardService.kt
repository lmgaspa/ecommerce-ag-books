package com.luizgasparetto.backend.monolito.services.author

import com.luizgasparetto.backend.monolito.dto.book.BookDashboardDto
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import com.luizgasparetto.backend.monolito.services.book.toDashboardDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthorDashboardService(
    private val bookRepository: BookRepository,
) {

    /**
     * Lista livros de um autor a partir do e-mail (painel do autor).
     * Usa sempre authors como fonte de verdade (authorRef).
     */
    @Transactional(readOnly = true)
    fun listBooksForAuthorEmail(email: String): List<BookDashboardDto> {
        val emailLower = email.trim().lowercase()

        val books = bookRepository.findAllByAuthorEmailLower(emailLower)

        return books.map { it.toDashboardDto() }
    }
}
