// src/main/kotlin/.../api/payout/PayoutSendDtos.kt
package com.luizgasparetto.backend.monolito.api.payout

import java.math.BigDecimal

data class PayoutSendReq(
    val preview: PayoutPreviewReq,               // usa o mesmo schema do preview
    val favoredKeyOverride: String? = null,      // opcional, senão usa application.yml
    val infoPagador: String? = "Repasse Ecommerce",
    val idEnvio: String? = null                  // se não vier, geramos um
)

data class PayoutSendRes(
    val preview: PayoutPreviewRes,
    val idEnvio: String,
    val status: String,
    val endToEndId: String?
)
