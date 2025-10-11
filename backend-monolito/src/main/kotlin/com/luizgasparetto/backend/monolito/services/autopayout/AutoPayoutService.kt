// src/main/kotlin/com/luizgasparetto/backend/monolito/services/AutoPayoutService.kt
package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.clients.efi.EfiAutoPayoutClient
import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AutoPayoutService(
    private val cfg: AutoPayoutConfig,
    private val efiAutoPayoutClient: EfiAutoPayoutClient
) {
    fun sendAutoPayout(
        amountBRL: String,
        favoredKeyOverride: String? = null,
        info: String = "Automatic Payout",
        idempotencyId: String = UUID.randomUUID().toString().replace("-", "")
    ): Map<String, Any> {
        val favoredKey = (favoredKeyOverride ?: cfg.payoutFavoredKey).takeIf { it.isNotBlank() }
            ?: error("Favored PIX key not set. Provide favoredKeyOverride or set efi.payout.favored-key")

        val payload = mapOf(
            "valor" to amountBRL,
            "pagador" to mapOf(
                "chave" to cfg.pixKey,
                "infoPagador" to info
            ),
            "favorecido" to mapOf(
                "chave" to favoredKey
            )
        )
        return efiAutoPayoutClient.sendToKey(idempotencyId, payload)
    }
}
