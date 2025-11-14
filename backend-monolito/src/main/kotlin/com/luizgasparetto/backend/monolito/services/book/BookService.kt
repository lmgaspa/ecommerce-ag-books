package com.luizgasparetto.backend.monolito.services.book

import com.luizgasparetto.backend.monolito.dto.book.BookDTO
import com.luizgasparetto.backend.monolito.models.book.Book
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(private val bookRepository: BookRepository) {

    fun getAllBooks(): List<BookDTO> =
        bookRepository.findAll().map { it.toDto() }

    fun getBookDtoById(id: String): BookDTO =
        bookRepository.findById(id)
            .map { it.toDto() }
            .orElseThrow { NoSuchElementException("Livro $id não encontrado") }

    fun getBookById(id: String): Book =
        bookRepository.findById(id)
            .orElseThrow { RuntimeException("Livro não encontrado") }

    fun validateStock(id: String, amount: Int) {
        val book = getBookById(id)
        if ((book.stock ?: 0) < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
    }

    fun getImageUrl(bookId: String): String =
        getBookById(bookId).imageUrl               // se entity for nullable: `?: ""`

    @Transactional
    fun reserveOrThrow(bookId: String, qty: Int) {
        val changed = bookRepository.tryReserve(bookId, qty)
        if (changed != 1) {
            val title = runCatching { getBookById(bookId).title }.getOrNull() ?: bookId
            throw IllegalStateException("Indisponível: '$title'")
        }
    }

    @Transactional
    fun release(bookId: String, qty: Int) {
        bookRepository.release(bookId, qty)
    }
}
