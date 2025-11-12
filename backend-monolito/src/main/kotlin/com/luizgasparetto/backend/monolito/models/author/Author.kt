package com.luizgasparetto.backend.monolito.models.author

import jakarta.persistence.*

@Entity
@Table(name = "authors")
data class Author(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    var name: String,
    @Column(nullable = false, unique = true)
    val email: String
)
