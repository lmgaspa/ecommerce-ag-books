package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.clients.efi.EfiAutoPayoutClient
import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import com.luizgasparetto.backend.monolito.models.autopayout.request.AutoPayoutRequest
import com.luizgasparetto.backend.monolito.models.autopayout.response.AutoPayoutResponse
import com.luizgasparetto.backend.monolito.models.autopayout.response.toAutoPayoutResponse
import org.springframework.stereotype.Service

@Service
class AutoPayoutService(
    private val cfg: AutoPayoutConfig,
    private val efiClient: EfiAutoPayoutClient
) {
    /** Chamado pelo controller: PUT /api/autopayout/{transferId} */
    fun send(transferId: String, req: AutoPayoutRequest): AutoPayoutResponse {
        val favoredKey = (req.favoredKey?.takeIf { it.isNotBlank() }
            ?: cfg.payoutFavoredKey)?.takeIf { it.isNotBlank() }
            ?: error("Favored PIX key not set. Provide favoredKey in request or set efi.payout.favored-key")

        val payload = mapOf(
            "valor" to req.amountBRL, // "123.45"
            "pagador" to mapOf(
                "chave" to cfg.pixKey,
                "infoPagador" to (req.message ?: "Automatic Payout")
            ),
            "favorecido" to mapOf(
                "chave" to favoredKey
            )
        )

        val raw: Map<String, Any> = efiClient.sendToKey(transferId, payload)
        return raw.toAutoPayoutResponse()
    }
}
