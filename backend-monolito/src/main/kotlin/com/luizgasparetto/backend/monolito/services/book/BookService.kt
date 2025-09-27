package com.luizgasparetto.backend.monolito.services.book

import com.luizgasparetto.backend.monolito.dto.book.BookDTO
import com.luizgasparetto.backend.monolito.models.book.Book
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookService(private val bookRepository: BookRepository) {

    private fun toDto(it: Book): BookDTO {
        val s = it.stock ?: 0
        return BookDTO(
            id = it.id,
            title = it.title,
            imageUrl = it.imageUrl,
            price = it.price,
            description = it.description,
            author = it.author ?: "Desconhecido",
            category = it.category ?: "Desconhecido",
            stock = s,
            available = s > 0
        )
    }

    fun getAllBooks(): List<BookDTO> = bookRepository.findAll().map(::toDto)

    fun getBookDtoById(id: String): BookDTO =
        bookRepository.findById(id).map(::toDto)
            .orElseThrow { NoSuchElementException("Livro $id não encontrado") }

    fun getBookById(id: String): Book =
        bookRepository.findById(id).orElseThrow { RuntimeException("Livro não encontrado") }

    /** Checagem rápida (não garante contra corrida; a reserva que garante) */
    fun validateStock(id: String, amount: Int) {
        val book = getBookById(id)
        if ((book.stock ?: 0) < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
    }

    fun getImageUrl(bookId: String): String =
        getBookById(bookId).imageUrl ?: ""

    /** Reserva atômica: única fonte de verdade para evitar oversell. */
    @Transactional
    fun reserveOrThrow(bookId: String, qty: Int) {
        val changed = bookRepository.tryReserve(bookId, qty)
        if (changed != 1) {
            val title = runCatching { getBookById(bookId).title }.getOrNull() ?: bookId
            throw IllegalStateException("Indisponível: '$title'")
        }
    }

    /** Libera reserva (reaper/cancelamento). */
    @Transactional
    fun release(bookId: String, qty: Int) {
        bookRepository.release(bookId, qty)
    }

    /** (Opcional) Evite usar fora: reserva + confirmação já tratam o ciclo. */
    @Deprecated("Use reserveOrThrow/release no fluxo de reserva TTL")
    @Transactional
    fun updateStock(id: String, amount: Int) {
        // Mantida apenas por compatibilidade — não use no webhook
        val book = getBookById(id)
        if ((book.stock ?: 0) < amount) {
            throw IllegalArgumentException("Estoque insuficiente para o livro '${book.title}'")
        }
        book.stock = (book.stock ?: 0) - amount
        bookRepository.save(book)
    }
}