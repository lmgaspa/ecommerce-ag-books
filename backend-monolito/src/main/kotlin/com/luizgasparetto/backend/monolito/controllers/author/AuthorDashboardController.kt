package com.luizgasparetto.backend.monolito.controllers.author

import com.luizgasparetto.backend.monolito.dto.book.BookDashboardDto
import com.luizgasparetto.backend.monolito.services.author.AuthorDashboardService
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("${ApiRoutes.API_V1}/dashboard/author")
class AuthorDashboardController(
    private val authorDashboardService: AuthorDashboardService,
) {

    // Ex.: GET /api/v1/dashboard/author/books?email=ag1957@gmail.com
    @GetMapping("/books")
    fun listBooksForAuthor(
        @RequestParam email: String
    ): ResponseEntity<List<BookDashboardDto>> {
        val dtos = authorDashboardService.listBooksForAuthorEmail(email)
        return ResponseEntity.ok(dtos)
    }
}
