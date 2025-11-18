package com.luizgasparetto.backend.monolito.services.book

import com.luizgasparetto.backend.monolito.dto.book.BookDTO
import com.luizgasparetto.backend.monolito.models.book.Book
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(
    private val bookRepository: BookRepository
) {

    private fun Book.toDto(): BookDTO = BookDTO(
        id = this.id,
        title = this.title,
        imageUrl = this.imageUrl,
        price = this.price,
        description = this.description ?: "",
        author = this.author,
        category = this.category,
        stock = this.stock,
        available = this.stock > 0
    )

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
        if (book.stock < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
    }

    fun getImageUrl(bookId: String): String =
        getBookById(bookId).imageUrl

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

    @Deprecated("Use reserveOrThrow/release no fluxo de reserva TTL")
    @Transactional
    fun updateStock(id: String, amount: Int) {
        val book = getBookById(id)
        if (book.stock < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
        book.stock = book.stock - amount
        bookRepository.save(book)
    }
}
