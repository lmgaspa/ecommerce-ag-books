package com.luizgasparetto.backend.monolito.bootstrap

import com.luizgasparetto.backend.monolito.models.author.Author
import com.luizgasparetto.backend.monolito.repositories.AuthorRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Component
class AuthorBootstrapRunner(
    private val props: BootstrapAuthorProperties,
    private val authorRepo: AuthorRepository,
    private val jdbc: NamedParameterJdbcTemplate
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!props.enabled) {
            log.info("[bootstrap.author] disabled — skipping")
            return
        }

        val name  = props.name?.trim().orEmpty()
        val email = props.email?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        require(name.isNotBlank())  { "bootstrap.author.name is required" }
        require(email.isNotBlank()) { "bootstrap.author.email is required" }

        val ids: List<String> = when (props.mode.lowercase(Locale.getDefault())) {
            "ids" -> props.bookIds.orEmpty()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else  -> findBookIdsByAuthorText(name) // default = text
        }

        if (ids.isEmpty()) {
            log.warn("[bootstrap.author] no books found for name='{}' (mode={})", name, props.mode)
            return
        }

        log.info("[bootstrap.author] START name='{}' email='{}' mode={} onlyUnmapped={} bookIds(size={})",
            name, email, props.mode, props.onlyUnmapped, ids.size)

        val author = upsertAuthor(name, email)
        mapBooksToAuthor(ids, authorEmail = author.email, onlyUnmapped = props.onlyUnmapped)

        log.info("[bootstrap.author] DONE — mapped {} books to {}", ids.size, author.email)
    }

    /** Busca robusta por texto (TRIM/ILIKE) em books.author. */
    private fun findBookIdsByAuthorText(authorName: String): List<String> {
        val sql = """
            SELECT id
            FROM books
            WHERE TRIM(author) ILIKE :a
        """.trimIndent()
        val like = authorName.trim()
        val p = MapSqlParameterSource().addValue("a", like)
        return jdbc.query(sql, p) { rs, _ -> rs.getString("id") }
    }

    @Transactional
    fun upsertAuthor(name: String, email: String): Author {
        val existing = authorRepo.findByEmail(email)
        if (existing != null) {
            if (existing.name != name) {
                existing.name = name
                authorRepo.save(existing)
                log.info("[bootstrap.author] updated name id={} email={}", existing.id, email)
            } else {
                log.info("[bootstrap.author] author exists id={} email={}", existing.id, email)
            }
            return existing
        }
        val saved = authorRepo.save(Author(name = name, email = email))
        log.info("[bootstrap.author] inserted id={} email={}", saved.id, email)
        return saved
    }

    /** UPSERT em payment_book_authors: insere os que faltam e corrige os que estiverem errados. */
    @Transactional
    fun mapBooksToAuthor(bookIds: List<String>, authorEmail: String, onlyUnmapped: Boolean) {
        // Garante que vamos ter o author_id alvo
        val authorId: Long = jdbc.queryForObject(
            "SELECT id FROM payment_author_registry WHERE lower(email)=lower(:e)",
            MapSqlParameterSource().addValue("e", authorEmail),
            Long::class.java
        )!!

        // 1) Insere vínculos que não existem
        jdbc.update(
            """
            INSERT INTO payment_book_authors (book_id, author_id)
            SELECT b.book_id, :aid
            FROM (SELECT unnest(:ids)::varchar AS book_id) b
            WHERE NOT EXISTS (
              SELECT 1 FROM payment_book_authors p WHERE p.book_id = b.book_id
            );
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("ids", bookIds.toTypedArray())
                .addValue("aid", authorId)
        )

        // 2) Atualiza vínculos — só os “vazios” ou tudo (conforme onlyUnmapped)
        val where = if (onlyUnmapped)
            "WHERE p.book_id = ANY(:ids) AND p.author_id IS NULL"
        else
            "WHERE p.book_id = ANY(:ids)"

        jdbc.update(
            """
            UPDATE payment_book_authors p
            SET author_id = :aid
            $where;
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("ids", bookIds.toTypedArray())
                .addValue("aid", authorId)
        )
    }
}
