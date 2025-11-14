package com.luizgasparetto.backend.monolito.controllers.book

import com.luizgasparetto.backend.monolito.dto.book.BookDashboardDto
import com.luizgasparetto.backend.monolito.services.book.BookDashboardService
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints específicos pro painel / dashboard (autor + email).
 */
@RestController
@RequestMapping("${ApiRoutes.API_V1}/dashboard/books")
class BookDashboardController(
    private val bookDashboardService: BookDashboardService
) {

    @GetMapping
    fun listAll(): ResponseEntity<List<BookDashboardDto>> =
        ResponseEntity.ok(bookDashboardService.listAllBooks())

    @GetMapping("/author/{authorId}")
    fun listByAuthor(@PathVariable authorId: Long): ResponseEntity<List<BookDashboardDto>> =
        ResponseEntity.ok(bookDashboardService.listBooksByAuthor(authorId))
}
