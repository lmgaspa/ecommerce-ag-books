package com.luizgasparetto.backend.monolito.controllers.order

import com.luizgasparetto.backend.monolito.dto.order.OrderEmailDTO
import com.luizgasparetto.backend.monolito.models.order.OrderEmailStatus
import com.luizgasparetto.backend.monolito.repositories.OrderEmailRepository
import com.luizgasparetto.backend.monolito.web.ApiRoutes
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("${ApiRoutes.API_V1}/orders")
class OrderEmailController(private val orderEmailRepository: OrderEmailRepository) {
    @GetMapping("/emails")
    fun listAllEmails(
            @RequestParam(required = false) orderId: Long?,
            @RequestParam(required = false) emailType: String?,
            @RequestParam(required = false) status: String?
    ): ResponseEntity<List<OrderEmailDTO>> {
        val emails =
                when {
                    orderId != null && emailType != null ->
                            orderEmailRepository.findByOrderIdAndEmailType(orderId, emailType)
                    orderId != null -> orderEmailRepository.findByOrderId(orderId)
                    emailType != null -> orderEmailRepository.findByEmailType(emailType)
                    else -> orderEmailRepository.findAll()
                }

        val filtered =
                if (status != null) {
                    val statusEnum =
                            try {
                                OrderEmailStatus.valueOf(status.uppercase())
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                    if (statusEnum != null) {
                        emails.filter { it.status == statusEnum }
                    } else {
                        emails
                    }
                } else {
                    emails
                }

        val dtos = filtered.map { OrderEmailDTO.from(it) }.sortedByDescending { it.sentAt }

        return ResponseEntity.ok(dtos)
    }

    @GetMapping("/{orderId}/emails")
    fun getEmailsByOrderId(@PathVariable orderId: Long): ResponseEntity<List<OrderEmailDTO>> {
        val emails = orderEmailRepository.findByOrderId(orderId)
        val dtos = emails.map { OrderEmailDTO.from(it) }.sortedByDescending { it.sentAt }
        return ResponseEntity.ok(dtos)
    }
}
