package com.luizgasparetto.backend.monolito.models.book

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "books")
data class Book(

    /** PK é VARCHAR(255). NÃO gere UUID automático aqui, pois você usa slugs como 'extase','sempre'. */
    @Id
    @Column(length = 255, nullable = false)
    val id: String,

    /** No schema é NOT NULL. */
    @Column(nullable = false)
    val author: String,

    /** No schema é NOT NULL. */
    @Column(nullable = false)
    val category: String,

    /** No schema pode ser NULL. */
    @Column(columnDefinition = "TEXT", nullable = true)
    val description: String? = null,

    /** No schema é image_url NOT NULL. */
    @Column(name = "image_url", nullable = false)
    val imageUrl: String,

    /** No schema é double precision NOT NULL. (Se quiser precisão monetária, depois migra para BigDecimal). */
    @Column(nullable = false)
    val price: Double,

    /** No schema é integer NOT NULL. */
    @Column(nullable = false)
    var stock: Int = 0,

    /** No schema é NOT NULL. */
    @Column(nullable = false)
    val title: String
)
