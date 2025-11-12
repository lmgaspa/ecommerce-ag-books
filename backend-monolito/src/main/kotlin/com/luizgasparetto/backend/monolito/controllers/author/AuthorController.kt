package com.luizgasparetto.backend.monolito.controllers.author

import com.luizgasparetto.backend.monolito.dto.author.AuthorDTO
import com.luizgasparetto.backend.monolito.dto.author.AuthorUpsertDTO
import com.luizgasparetto.backend.monolito.services.author.AuthorService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Validated
@RestController
@RequestMapping("/api/authors")
class AuthorController(
    private val authorService: AuthorService
) {
    @GetMapping fun list(): List<AuthorDTO> = authorService.list()

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): AuthorDTO = authorService.getById(id)

    @GetMapping("/by-email")
    fun getByEmail(@RequestParam @Email email: String): AuthorDTO = authorService.getByEmail(email)

    @PostMapping
    fun create(@Valid @RequestBody body: AuthorUpsertDTO): AuthorDTO = authorService.create(body)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody body: AuthorUpsertDTO): AuthorDTO =
        authorService.update(id, body)

    @PostMapping("/upsert-by-email")
    fun upsertByEmail(@Valid @RequestBody body: AuthorUpsertDTO): AuthorDTO =
        authorService.upsertByEmail(body)
}
