package com.luizgasparetto.backend.monolito.payment.services

import com.luizgasparetto.backend.monolito.payment.repo.PaymentWebhookEventEntity
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.luizgasparetto.backend.monolito.payment.repo.PaymentWebhookEventRepository
import org.springframework.stereotype.Service

@Service
class PaymentRawEventService(private val repo: PaymentWebhookEventRepository) {
    private val mapper = jacksonObjectMapper()

    fun saveRaw(provider: String, eventType: String, externalId: String, orderRef: String?, payload: Any) {
        runCatching {
            repo.save(
                PaymentWebhookEventEntity(
                    id = null, // << deixe null para o IDENTITY gerar,
                    provider = provider,
                    eventType = eventType,
                    externalId = externalId,
                    orderRef = orderRef,
                    payload = mapper.valueToTree(payload)
                )
            )
        }
        // UNIQUE impede duplicação (idempotente); silêncio em caso de conflito
    }
}
