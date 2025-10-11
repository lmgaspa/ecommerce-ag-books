// src/main/kotlin/com/luizgasparetto/backend/monolito/controllers/webhook/EfiAutoPayoutWebhookController.kt
package com.luizgasparetto.backend.monolito.controllers.webhook

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/webhooks/efi/autopayout")
class EfiAutoPayoutWebhookController {
    @PostMapping
    fun handle(@RequestBody body: Map<String, Any>): ResponseEntity<Unit> {
        // TODO: validate signature (if any) and process status changes: EM_PROCESSAMENTO / REALIZADO / NAO_REALIZADO
        return ResponseEntity.ok().build()
    }
}
