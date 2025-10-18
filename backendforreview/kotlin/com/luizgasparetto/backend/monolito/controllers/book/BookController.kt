package com.luizgasparetto.backend.monolito.controllers.book

import com.luizgasparetto.backend.monolito.dto.book.BookDTO
import com.luizgasparetto.backend.monolito.services.book.BookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/books")
class BookController(private val bookService: BookService) {

    @GetMapping
    fun listBooks(): ResponseEntity<List<BookDTO>> =
        ResponseEntity.ok(bookService.getAllBooks())

    @GetMapping("/{id}")
    fun getBook(@PathVariable id: String): ResponseEntity<BookDTO> =
        ResponseEntity.ok(bookService.getBookDtoById(id))
}