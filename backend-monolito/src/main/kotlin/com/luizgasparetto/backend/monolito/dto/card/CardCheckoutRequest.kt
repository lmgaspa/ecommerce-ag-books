package com.luizgasparetto.backend.monolito.dto.card

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CardCheckoutRequest(
    val firstName: String,
    val lastName: String,
    val cpf: String,
    val country: String?,                 // já era opcional no seu código
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
    val payment: String? = null,          // "card" (ideal) | null

    val shipping: Double,
    val cartItems: List<CardCartItemDto>,
    val total: Double,                    // conferido no servidor

    // específicos de cartão
    @JsonAlias("payment_token", "cardToken", "card_token")
    val paymentToken: String,             // token da SDK Efí (obrigatório p/ cartão)
    val installments: Int = 1             // parcelas
)
