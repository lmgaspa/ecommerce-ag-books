package com.luizgasparetto.backend.monolito.dto.payment

data class PaymentSiteAuthorDTO(
    val id: Long? = null,
    val name: String,
    val email: String? = null,
    val pixKey: String,
    val active: Boolean = true
)

data class PaymentSiteAuthorCreateRequest(
    val name: String,
    val email: String? = null,
    val pixKey: String,
    val active: Boolean = true
)

data class PaymentSiteAuthorUpdateRequest(
    val name: String? = null,
    val email: String? = null,
    val pixKey: String? = null,
    val active: Boolean? = null
)
