package com.luizgasparetto.backend.monolito.dto.book

data class BookDTO(
    val id: String,
    val title: String,
    val imageUrl: String,
    val price: Double,
    val description: String,
    val author: String,
    val category: String,
    val stock: Int,
    val available: Boolean
)