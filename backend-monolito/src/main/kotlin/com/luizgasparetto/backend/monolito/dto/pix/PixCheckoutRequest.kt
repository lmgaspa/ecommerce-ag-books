package com.luizgasparetto.backend.monolito.dto.pix

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PixCheckoutRequest(
    val firstName: String,
    val lastName: String,
    val cpf: String,
    val country: String?,                 // manter alinhado ao de cartão
    val cep: String,
    val address: String,
    val number: String,
    val complement: String?,
    val district: String,
    val city: String,
    val state: String,
    val phone: String,
    val email: String,
    val note: String?,

    // redundância/validação (pode vir null)
    val payment: String? = null,          // "pix" (ideal) | null

    val shipping: Double,
    val cartItems: List<PixCartItemDto>,
    val total: Double                     // conferido no servidor
)