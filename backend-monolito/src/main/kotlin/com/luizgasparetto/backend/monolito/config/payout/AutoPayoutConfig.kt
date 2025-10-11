// src/main/kotlin/com/luizgasparetto/backend/monolito/config/AutoPayoutConfig.kt
package com.luizgasparetto.backend.monolito.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AutoPayoutConfig(
    @Value("\${efi.pix.client-id}")      val clientId: String,
    @Value("\${efi.pix.client-secret}")  val clientSecret: String,
    @Value("\${efi.pix.chave}")          val pixKey: String,
    @Value("\${efi.pix.cert-path}")      val certPath: String,
    @Value("\${efi.pix.cert-password:}") val certPassword: String,
    @Value("\${efi.pix.sandbox:false}")  val sandbox: Boolean,

    @Value("\${efi.card.client-id}")      val cardClientId: String,
    @Value("\${efi.card.client-secret}")  val cardClientSecret: String,
    @Value("\${efi.card.sandbox:false}")  val cardSandbox: Boolean,

    // 🔽 chave PIX do favorecido padrão (repasses)
    @Value("\${efi.payout.favored-key:}") val payoutFavoredKey: String
) {
    val environment: String get() = if (sandbox) "production" else "production" // mantenha se não usar aqui
}
