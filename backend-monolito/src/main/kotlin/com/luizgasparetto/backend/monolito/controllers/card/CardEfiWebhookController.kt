package com.luizgasparetto.backend.monolito.controllers.card

import com.fasterxml.jackson.databind.ObjectMapper
import com.luizgasparetto.backend.monolito.models.webhook.WebhookEvent
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import com.luizgasparetto.backend.monolito.repositories.WebhookEventRepository
import com.luizgasparetto.backend.monolito.services.card.CardPaymentProcessor
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/efi-webhook/card")
class CardEfiWebhookController(
    private val mapper: ObjectMapper,
    private val orders: OrderRepository,
    private val processor: CardPaymentProcessor,
    private val webhookRepo: WebhookEventRepository
) {
    private val log = LoggerFactory.getLogger(CardEfiWebhookController::class.java)

    @PostMapping(consumes = ["application/json"])
    fun handle(@RequestBody rawBody: String): ResponseEntity<String> {
        log.info("EFI CARD WEBHOOK RAW={}", rawBody.take(4000))

        val root = runCatching { mapper.readTree(rawBody) }.getOrElse {
            log.warn("CARD WEBHOOK: JSON inválido: {}", it.message)
            webhookRepo.save(
                WebhookEvent(
                    txid = null,
                    status = "INVALID_JSON",
                    chargeId = null,
                    provider = "CARD",
                    rawBody = rawBody,
                    receivedAt = OffsetDateTime.now()
                )
            )
            return ResponseEntity.ok("ignored: invalid json")
        }

        val chargeId = listOf(
            root.path("charge_id"),
            root.path("data").path("charge_id"),
            root.path("identifiers").path("charge_id"),
            root.path("charge").path("id"),
            root.path("data").path("charge").path("id"),
            root.path("payment").path("charge_id")
        ).firstOrNull { !it.isMissingNode && !it.isNull && it.asText().isNotBlank() }?.asText()

        val status = listOf(
            root.path("status"),
            root.path("data").path("status"),
            root.path("payment").path("status"),
            root.path("charge").path("status"),
            root.path("data").path("charge").path("status"),
            root.path("transaction").path("status")
        ).firstOrNull { !it.isMissingNode && !it.isNull && it.asText().isNotBlank() }?.asText()

        // Resolve o pedido (se possível) para também armazenar o txid no histórico
        val order = chargeId?.let { orders.findWithItemsByChargeId(it) }

        // Persistimos SEMPRE para auditoria
        webhookRepo.save(
            WebhookEvent(
                txid = order?.txid,           // se achou o pedido, guarda o txid dele
                status = status,
                rawBody = rawBody,
                chargeId = chargeId,
                provider = "CARD",
                receivedAt = OffsetDateTime.now()
            )
        )

        if (chargeId == null) {
            log.info("CARD WEBHOOK: ignorado, sem charge_id")
            return ResponseEntity.ok("ignored: no charge_id")
        }
        if (status == null) {
            log.info("CARD WEBHOOK: ignorado, sem status (chargeId={})", chargeId)
            return ResponseEntity.ok("ignored: no status")
        }

        if (order == null) {
            log.info("CARD WEBHOOK: order not found for chargeId={}, status={}", chargeId, status)
            return ResponseEntity.ok("ignored: order not found")
        }

        val paid = processor.isCardPaidStatus(status)
        val applied = if (paid) processor.markPaidIfNeededByChargeId(chargeId) else false

        log.info(
            "CARD WEBHOOK: chargeId={}, status={}, paidLike={}, applied={}, orderId={}",
            chargeId, status, paid, applied, order.id
        )
        return ResponseEntity.ok("status=$status; applied=$applied")
    }
}
