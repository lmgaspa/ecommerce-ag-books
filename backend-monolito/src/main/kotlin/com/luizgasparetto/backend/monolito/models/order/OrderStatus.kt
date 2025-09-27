package com.luizgasparetto.backend.monolito.models.order

/**
 * Status da *ordem* no domínio da loja (não confundir com status específico da Efí).
 * Pensado para cobrir o fluxo de cartão (autorização, captura, etc.) e unificar com o resto.
 */
enum class OrderStatus {
    // Criação/espera
    NEW,                // cobrança criada no seu sistema
    WAITING,            // aguardando confirmação/pagamento (ex.: "waiting" na Efí)
    PROCESSING,         // processando análise/fraude/operadora
    // Cartão (autorização/captura)
    AUTHORIZED,         // autorizado pela operadora (pré-captura)
    APPROVED,           // aprovado (Efí: "approved" = aprovado pela operadora)
    CAPTURED,           // capturado (valor capturado)
    CONFIRMED,          // CONFIRMED (sinônimo útil p/ integrações que usam "confirmed")
    PAID,               // pago/creditado
    // Falhas/cancelamentos
    UNPAID,             // não pago (Efí: "unpaid")
    DECLINED,           // recusado pela operadora/anti-fraude
    CANCELED,           // cancelado
    REFUNDED,           // estornado total
    PARTIALLY_REFUNDED, // estorno parcial
    CHARGEBACK,         // chargeback (devolução pelo emissor)
    EXPIRED,            // expirado (se aplicável ao seu caso)
    ERROR;              // erro genérico de integração

    /** Tratativa do frontend: considerar como "pago" estes status. */
    fun isPaidLike(): Boolean = when (this) {
        APPROVED, CAPTURED, CONFIRMED, PAID -> true
        else -> false
    }

    /** Estados terminais que não devem mais mudar automaticamente. */
    fun isFinal(): Boolean = when (this) {
        PAID, CANCELED, REFUNDED, PARTIALLY_REFUNDED, CHARGEBACK, EXPIRED, ERROR -> true
        else -> false
    }

    companion object {
        /**
         * Mapeia status de *transação Efí* para o seu domínio.
         * Ajuste aqui se a Efí introduzir novos valores.
         *
         * Alguns conhecidos:
         * - "new"        -> NEW
         * - "waiting"    -> WAITING
         * - "approved"   -> APPROVED
         * - "paid"       -> PAID
         * - "unpaid"     -> UNPAID
         * - "canceled"   -> CANCELED
         * - "refunded"   -> REFUNDED
         * - "chargeback" -> CHARGEBACK
         * - "processing" -> PROCESSING
         * - "confirmed"  -> CONFIRMED
         */
        fun fromEfi(status: String?): OrderStatus = when (status?.lowercase()) {
            "new"          -> NEW
            "waiting"      -> WAITING
            "processing"   -> PROCESSING
            "authorized"   -> AUTHORIZED
            "approved"     -> APPROVED
            "captured"     -> CAPTURED
            "confirmed"    -> CONFIRMED
            "paid"         -> PAID
            "unpaid"       -> UNPAID
            "declined"     -> DECLINED
            "canceled"     -> CANCELED
            "refunded"     -> REFUNDED
            "partially_refunded" -> PARTIALLY_REFUNDED
            "chargeback"   -> CHARGEBACK
            "expired"      -> EXPIRED
            else           -> ERROR
        }
    }
}

/** Atalho: converte string da Efí direto para OrderStatus. */
fun String?.toOrderStatusFromEfi(): OrderStatus = OrderStatus.fromEfi(this)
