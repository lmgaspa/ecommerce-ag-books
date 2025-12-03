package com.luizgasparetto.backend.monolito.exceptions

/**
 * Erro ao falar com o gateway de pagamento (Efí, etc).
 *
 * Usamos para:
 * - Timeout / 5xx do gateway
 * - 401/403 quando credenciais ou permissões estão erradas
 * - Qualquer falha em que o cliente NÃO tem culpa direta.
 */
class PaymentGatewayException(
    override val message: String,
    val gatewayCode: String? = null
) : RuntimeException(message)
