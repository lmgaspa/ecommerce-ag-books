package com.luizgasparetto.backend.monolito.privacy

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConsentRequest(
        val analytics: Boolean,
        val marketing: Boolean? = false,
        val version: Int = 1
)

data class ConsentView(
        val analytics: Boolean,
        val marketing: Boolean,
        val version: Int,
        val ts: Long
)
