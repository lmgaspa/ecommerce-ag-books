package com.luizgasparetto.backend.monolito.services.book

import com.luizgasparetto.backend.monolito.dto.book.BookDashboardDto
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Serviço específico para o painel / vitrine “autor-aware”.
 * Não mexe em estoque nem em reservas – só leitura.
 */
@Service
class BookDashboardService(
    private val bookRepository: BookRepository
) {

    @Transactional(readOnly = true)
    fun listAllBooks(): List<BookDashboardDto> =
        bookRepository.findAllWithAuthor()
            .map { it.toDashboardDto() }

    @Transactional(readOnly = true)
    fun listBooksByAuthor(authorId: Long): List<BookDashboardDto> =
        bookRepository.findAllByAuthorIdWithAuthor(authorId)
            .map { it.toDashboardDto() }
}
