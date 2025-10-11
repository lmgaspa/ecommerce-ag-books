package com.luizgasparetto.backend.monolito.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Lê propriedades com prefixo: efi.payout.pix
 * Ex.:
 *   efi:
 *     payout:
 *       pix:
 *         client-id: ...
 *         client-secret: ...
 *         chave: ...
 *         cert-path: classpath:producao-ec-agenor.p12
 *         cert-password: ""
 *         sandbox: false
 *       favored-key: ...
 */
@Configuration
@ConfigurationProperties(prefix = "efi.payout.pix")
class AutoPayoutConfig {

    lateinit var clientId: String
    lateinit var clientSecret: String
    lateinit var chave: String
    lateinit var certPath: String
    var certPassword: String? = null
    var sandbox: Boolean = false

    /** chave destino padrão (opcional) */
    @Value("\${efi.payout.favored-key:}")
    var payoutFavoredKey: String? = null

    /** helpers */
    val pixKey: String get() = chave
    val environment: String get() = if (sandbox) "sandbox" else "production"
}
