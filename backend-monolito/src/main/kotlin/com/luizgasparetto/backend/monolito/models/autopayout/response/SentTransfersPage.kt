package com.luizgasparetto.backend.monolito.models.autopayout.response

data class SentTransfersPage<T>(
    val content: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
)
