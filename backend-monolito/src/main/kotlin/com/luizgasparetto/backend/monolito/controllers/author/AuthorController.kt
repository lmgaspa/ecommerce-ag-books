package com.luizgasparetto.backend.monolito.controllers.author

import com.luizgasparetto.backend.monolito.dto.author.AuthorDTO
import com.luizgasparetto.backend.monolito.services.author.AuthorService
import org.springframework.web.bind.annotation.*
import com.luizgasparetto.backend.monolito.web.ApiRoutes

@RestController
@RequestMapping("${ApiRoutes.API_V1}/authors")
class AuthorController(
    private val authorService: AuthorService
) {
    @GetMapping
    fun list(): List<AuthorDTO> = authorService.list()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): AuthorDTO = authorService.getById(id)

    @GetMapping("/by-email")
    fun getByEmail(@RequestParam email: String): AuthorDTO = authorService.getByEmail(email)

    @PostMapping
    fun upsert(@RequestBody body: UpsertAuthorRequest): AuthorDTO =
        authorService.upsert(name = body.name.trim(), email = body.email.trim().lowercase())
}

data class UpsertAuthorRequest(
    val name: String,
    val email: String
)
