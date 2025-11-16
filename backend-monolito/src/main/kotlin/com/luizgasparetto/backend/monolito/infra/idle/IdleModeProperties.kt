package com.luizgasparetto.backend.monolito.infra.idle

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue

@ConfigurationProperties("idle.mode")
data class IdleModeProperties(
    @DefaultValue("false")
    val enabled: Boolean = false,
    @DefaultValue("true")
    val wakeOnFirstRequest: Boolean = true,
    @DefaultValue("20")
    val timeoutMinutes: Long = 20
)


