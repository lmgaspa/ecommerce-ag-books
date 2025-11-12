package com.luizgasparetto.backend.monolito.models.author

import jakarta.persistence.*

@Entity
@Table(name = "payment_author_registry")
class Author(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false, unique = false) // no schema atual pode não ser unique
    var email: String
)
