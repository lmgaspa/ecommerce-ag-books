package com.luizgasparetto.backend.monolito.models.author

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "authors")
@Access(AccessType.FIELD)
class Author(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false) // unicidade garantida no índice funcional lower(email)
    var email: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null

) {
    protected constructor() : this(
        id = null,
        name = "",
        email = ""
    )

    override fun toString(): String =
        "Author(id=$id, name='$name', email='$email')"
}
