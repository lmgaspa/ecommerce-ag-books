package com.luizgasparetto.backend.monolito.bootstrap

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bootstrap.author")
data class BootstrapAuthorProperties(
    val enabled: Boolean = false,
    val name: String? = null,
    val email: String? = null,
    /** "text" (default) usa books.author; "ids" usa CSV em bookIds */
    val mode: String = "text",
    /** Só preenche onde está vazio; se false, força remapeamento */
    val onlyUnmapped: Boolean = true,
    /** Usado se mode = "ids" */
    val bookIds: String? = null
)
