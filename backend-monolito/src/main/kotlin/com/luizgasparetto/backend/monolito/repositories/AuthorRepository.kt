package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.author.Author
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AuthorRepository : JpaRepository<Author, Long> {
    @Query("select a from Author a where lower(a.email) = lower(?1)")
    fun findByEmailIgnoreCase(email: String): Author?
    fun existsByEmailIgnoreCase(email: String): Boolean
}
