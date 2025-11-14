package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.book.Book
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BookRepository : JpaRepository<Book, String> {

    // ---------- Leitura com autor carregado (painel / vitrine nova) ----------

    @Query(
        """
        SELECT b FROM Book b
        JOIN FETCH b.authorRef a
        """
    )
    fun findAllWithAuthor(): List<Book>

    @Query(
        """
        SELECT b FROM Book b
        JOIN FETCH b.authorRef a
        WHERE a.id = :authorId
        """
    )
    fun findAllByAuthorIdWithAuthor(@Param("authorId") authorId: Long): List<Book>

    // ---------- Fluxo de estoque atômico (já existente) ----------

    /** Reserva atômica: debita se (stock >= qty). Retorna linhas afetadas (0/1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Book b
           SET b.stock = b.stock - :qty
         WHERE b.id = :id
           AND COALESCE(b.stock, 0) >= :qty
        """
    )
    fun tryReserve(@Param("id") id: String, @Param("qty") qty: Int): Int

    /** Libera reserva/estoque (usado ao expirar reserva ou cancelar). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Book b
           SET b.stock = b.stock + :qty
         WHERE b.id = :id
        """
    )
    fun release(@Param("id") id: String, @Param("qty") qty: Int): Int
}
