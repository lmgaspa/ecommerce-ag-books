package com.luizgasparetto.backend.monolito.dto.coupon

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.luizgasparetto.backend.monolito.dto.card.CardCartItemDto
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class CouponValidationRequestDto(
    @field:NotBlank(message = "Código do cupom é obrigatório")
    val code: String,

    @field:NotNull(message = "Total do pedido é obrigatório")
    @field:DecimalMin(value = "0.0", inclusive = false, message = "Total do pedido deve ser maior que zero")
    val orderTotal: BigDecimal,

    val userEmail: String? = null,
    
    val cartItems: List<CardCartItemDto>? = null
)
