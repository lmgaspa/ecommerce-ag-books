package com.luizgasparetto.backend.monolito.controllers.order

import com.luizgasparetto.backend.monolito.services.order.OrderEventsPublisher
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/orders")
class OrderEventsController(
    private val events: OrderEventsPublisher
) {
    @GetMapping("/{orderId}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@PathVariable orderId: Long, response: HttpServletResponse): SseEmitter {
        response.setHeader("Cache-Control", "no-cache, no-transform")
        response.setHeader("Connection", "keep-alive")
        response.setHeader("X-Accel-Buffering", "no")
        // timeout padrão configurado dentro do subscribe (330_000 ms). O front pode reabrir depois.
        return events.subscribe(orderId)
    }
}
