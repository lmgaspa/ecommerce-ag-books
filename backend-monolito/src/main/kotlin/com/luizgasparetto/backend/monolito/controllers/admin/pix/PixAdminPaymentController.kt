package com.luizgasparetto.backend.monolito.controllers.admin.pix

import com.luizgasparetto.backend.monolito.services.pix.PixClient
import com.luizgasparetto.backend.monolito.services.pix.PixPaymentProcessor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/payments/pix")
class PixAdminPaymentController(
    private val pix: PixClient,
    private val processor: PixPaymentProcessor
) {
    @PostMapping("/reprocess/{txid}")
    fun reprocess(@PathVariable txid: String): ResponseEntity<String> {
        val status = pix.status(txid)
        val applied = processor.isPaidStatus(status) && processor.markPaidIfNeededByTxid(txid)
        return ResponseEntity.ok("status=$status; applied=$applied")
    }
}
