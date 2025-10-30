package com.luizgasparetto.backend.monolito.config.efi

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode

open class EfiException(
    message: String,
    val status: HttpStatusCode? = null,
    val responseBody: Any? = null,
    val headers: HttpHeaders? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class EfiAuthException(
    message: String,
    status: HttpStatusCode,
    body: Any?,
    headers: HttpHeaders?
) : EfiException(message, status, body, headers)

class EfiRateLimitException(
    message: String,
    val retryAfterSeconds: Long?,
    body: Any?,
    headers: HttpHeaders?
) : EfiException(message, HttpStatusCode.valueOf(429), body, headers)

class EfiClientException(
    message: String,
    status: HttpStatusCode,
    body: Any?,
    headers: HttpHeaders?
) : EfiException(message, status, body, headers)

class EfiServerException(
    message: String,
    status: HttpStatusCode,
    body: Any?,
    headers: HttpHeaders?
) : EfiException(message, status, body, headers)
