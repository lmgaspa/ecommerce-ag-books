package com.luizgasparetto.backend.monolito.models.book

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "books")
data class Book(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val imageUrl: String,

    @Column(nullable = false)
    val price: Double,

    @Column(columnDefinition = "TEXT")
    val description: String,

    val author: String? = null,
    val category: String?,
    var stock: Int?
)