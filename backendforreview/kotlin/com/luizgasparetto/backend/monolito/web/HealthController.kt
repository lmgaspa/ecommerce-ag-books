package com.luizgasparetto.backend.monolito.controllers.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/")
    fun root() = mapOf("status" to "ok")

    @GetMapping("/health")
    fun health() = mapOf("status" to "up")
}
