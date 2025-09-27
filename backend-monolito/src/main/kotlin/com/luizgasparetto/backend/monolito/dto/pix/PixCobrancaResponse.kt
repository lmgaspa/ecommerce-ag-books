package com.luizgasparetto.backend.monolito.dto.pix

data class PixCobrancaResponse(
    val pixCopiaECola: String,
    val imagemQrcodeBase64: String,
    val txid: String
)