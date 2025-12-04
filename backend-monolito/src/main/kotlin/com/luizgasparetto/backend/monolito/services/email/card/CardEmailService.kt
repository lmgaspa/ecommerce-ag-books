package com.luizgasparetto.backend.monolito.services.email.card

import com.luizgasparetto.backend.monolito.models.order.Order
import org.springframework.stereotype.Service

/**
 * Facade para envio de emails de Cartão.
 * Delega para classes específicas: CardClientApprovedEmailSender, CardAuthorApprovedEmailSender,
 * CardClientDeclinedEmailSender e CardAuthorDeclinedEmailSender
 */
@Service
class CardEmailService(
    private val clientApprovedSender: CardClientApprovedEmailSender,
    private val authorApprovedSender: CardAuthorApprovedEmailSender,
    private val clientDeclinedSender: CardClientDeclinedEmailSender,
    private val authorDeclinedSender: CardAuthorDeclinedEmailSender
) {

    /**
     * Envia email para cliente quando cartão é aprovado
     */
    fun sendCardClientEmail(order: Order) {
        clientApprovedSender.send(order)
    }

    /**
     * Envia email para autor quando cartão é aprovado
     */
    fun sendCardAuthorEmail(order: Order) {
        authorApprovedSender.send(order)
    }

    /**
     * Envia email para cliente quando cartão é recusado
     */
    fun sendClientCardDeclined(order: Order) {
        clientDeclinedSender.send(order)
    }

    /**
     * Envia email para autor quando cartão é recusado
     */
    fun sendAuthorCardDeclined(order: Order) {
        authorDeclinedSender.send(order)
    }

}

