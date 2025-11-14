package com.luizgasparetto.backend.monolito.services.book

import com.luizgasparetto.backend.monolito.dto.book.BookDTO
import com.luizgasparetto.backend.monolito.models.book.Book

fun Book.toDto(): BookDTO = BookDTO(
    id          = id,
    title       = title,
    imageUrl    = imageUrl,
    price       = price,
    description = description ?: "",
    author      = author,
    category    = category,
    stock       = stock,
    available   = stock > 0
)
