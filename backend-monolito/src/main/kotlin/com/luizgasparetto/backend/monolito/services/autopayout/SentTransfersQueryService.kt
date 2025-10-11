// src/main/kotlin/com/luizgasparetto/backend/monolito/services/autopayout/SentTransfersQueryService.kt
package com.luizgasparetto.backend.monolito.services.autopayout

import com.luizgasparetto.backend.monolito.clients.efi.EfiAutoPayoutClient
import com.luizgasparetto.backend.monolito.models.autopayout.response.SentTransfersPage
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class SentTransfersQueryService(
    private val client: EfiAutoPayoutClient
) {
    private val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun listSent(from: OffsetDateTime, to: OffsetDateTime, status: String?): SentTransfersPage {
        val resp = client.listSent(from.format(fmt), to.format(fmt), mapOf("status" to status))
        return SentTransfersPage.from(resp)
    }
}
