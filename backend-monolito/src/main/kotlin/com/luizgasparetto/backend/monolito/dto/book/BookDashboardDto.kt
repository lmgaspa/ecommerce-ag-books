// src/main/kotlin/com/luizgasparetto/backend/monolito/dto/book/BookDashboardDto.kt
package com.luizgasparetto.backend.monolito.dto.book

data class BookDashboardDto(
    val id: String,
    val title: String,
    val category: String,
    val price: Double,
    val stock: Int,
    val imageUrl: String,
    val authorName: String,
    val authorEmail: String,
)
