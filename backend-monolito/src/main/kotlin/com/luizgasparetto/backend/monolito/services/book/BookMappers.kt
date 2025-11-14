package com.luizgasparetto.backend.monolito.services.book

import com.luizgasparetto.backend.monolito.dto.book.BookDTO
import com.luizgasparetto.backend.monolito.dto.book.BookDashboardDto
import com.luizgasparetto.backend.monolito.models.book.Book

/**
 * Mapper básico usado pela vitrine (API pública de livros).
 */
fun Book.toDto(): BookDTO {
    val s = this.stock
    return BookDTO(
        id = this.id,
        title = this.title,
        imageUrl = this.imageUrl,
        price = this.price,
        description = this.description ?: "",
        author = this.author,          // ainda vindo do texto legado
        category = this.category,
        stock = s,
        available = s > 0
    )
}

/**
 * Mapper para o painel / dashboard do autor.
 * Sempre usa authors como fonte de verdade (authorRef).
 */
fun Book.toDashboardDto(): BookDashboardDto {
    val a = this.authorRef
        ?: error("Book ${this.id} sem authorRef (author_id nulo). Legacy author='${this.author}'")

    return BookDashboardDto(
        id = this.id,
        title = this.title,
        category = this.category,
        price = this.price,
        stock = this.stock,
        imageUrl = this.imageUrl,
        authorName = a.name,
        authorEmail = a.email
    )
}
