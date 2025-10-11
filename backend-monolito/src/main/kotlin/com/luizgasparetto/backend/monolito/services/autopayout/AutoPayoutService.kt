package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.clients.efi.EfiAutoPayoutClient
import com.luizgasparetto.backend.monolito.clients.efi.EfiAuthException
import com.luizgasparetto.backend.monolito.clients.efi.EfiClientException
import com.luizgasparetto.backend.monolito.clients.efi.EfiRateLimitException
import com.luizgasparetto.backend.monolito.clients.efi.EfiServerException
import com.luizgasparetto.backend.monolito.config.AutoPayoutConfig
import com.luizgasparetto.backend.monolito.models.autopayout.request.AutoPayoutRequest
import com.luizgasparetto.backend.monolito.models.autopayout.response.AutoPayoutResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class AutoPayoutService(
    private val cfg: AutoPayoutConfig,
    private val efiClient: EfiAutoPayoutClient
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val idEnvioRegex = Regex("^[A-Za-z0-9]{1,35}$")

    fun send(transferId: String, req: AutoPayoutRequest): AutoPayoutResponse {
        require(transferId.isNotBlank()) { "transferId não pode ser vazio" }
        require(idEnvioRegex.matches(transferId)) { "transferId inválido (use 1-35 chars alfanuméricos)" }
        require(Regex("^[0-9]{1,10}\\.[0-9]{2}\$").matches(req.amountBRL)) {
            "amountBRL inválido (use \"12.34\")"
        }

        MDC.put("transferId", transferId)
        val started = Instant.now()

        try {
            val favoredKey = (req.favoredKey ?: cfg.payout.favoredKey)
                ?.takeIf { it.isNotBlank() }
                ?: error("Favored PIX key not set. Informe no request ou configure efi.payout.favored-key")

            val payload = mapOf(
                "valor" to req.amountBRL,
                "pagador" to mapOf(
                    "chave" to cfg.pix.chave,
                    "infoPagador" to (req.message ?: "Automatic Payout")
                ),
                "favorecido" to mapOf("chave" to favoredKey)
            )

            log.info(
                "AUTO-PAYOUT START amount={} payerKey={} favoredKey={}",
                req.amountBRL, cfg.pix.chave, favoredKey
            )

            @Suppress("UNCHECKED_CAST")
            val raw = efiClient.sendToKey(transferId, payload) as Map<String, Any?>
            val dto = mapEfiSendResponseToDto(transferId, raw)

            val ms = Duration.between(started, Instant.now()).toMillis()
            log.info(
                "AUTO-PAYOUT SUCCESS status={} endToEndId={} value={} elapsedMs={}",
                dto.status, dto.endToEndId, dto.value, ms
            )
            return dto

        } catch (ex: EfiRateLimitException) {
            val ms = Duration.between(started, Instant.now()).toMillis()
            log.warn(
                "AUTO-PAYOUT RATE-LIMIT retryAfterSeconds={} http={} elapsedMs={} body={}",
                ex.retryAfterSeconds, ex.status?.value(), ms, ex.responseBody
            )
            throw ex

        } catch (ex: EfiAuthException) {
            val ms = Duration.between(started, Instant.now()).toMillis()
            log.error(
                "AUTO-PAYOUT AUTH-ERROR http={} elapsedMs={} body={}",
                ex.status?.value(), ms, ex.responseBody
            )
            throw ex

        } catch (ex: EfiClientException) {
            val ms = Duration.between(started, Instant.now()).toMillis()
            log.warn(
                "AUTO-PAYOUT CLIENT-ERROR http={} elapsedMs={} body={}",
                ex.status?.value(), ms, ex.responseBody
            )
            throw ex

        } catch (ex: EfiServerException) {
            val ms = Duration.between(started, Instant.now()).toMillis()
            log.error(
                "AUTO-PAYOUT SERVER-ERROR http={} elapsedMs={} body={}",
                ex.status?.value(), ms, ex.responseBody
            )
            throw ex

        } catch (ex: Exception) {
            val ms = Duration.between(started, Instant.now()).toMillis()
            log.error("AUTO-PAYOUT UNEXPECTED elapsedMs={} msg={}", ms, ex.message, ex)
            throw ex

        } finally {
            MDC.remove("transferId")
        }
    }

    fun sendAutoPayout(
        amountBRL: String,
        favoredKeyOverride: String? = null,
        info: String? = null,
        transferId: String
    ): AutoPayoutResponse =
        send(transferId, com.luizgasparetto.backend.monolito.models.autopayout.request.AutoPayoutRequest(
            amountBRL, favoredKeyOverride, info
        ))

    private fun mapEfiSendResponseToDto(
        transferId: String,
        body: Map<String, Any?>
    ): AutoPayoutResponse {
        val horario = body["horario"] as? Map<*, *>
        val favorecido = body["favorecido"] as? Map<*, *>
        val identificacao = favorecido?.get("identificacao") as? Map<*, *>
        val contaBanco = favorecido?.get("contaBanco") as? Map<*, *>
        val endToEnd = (body["e2eId"] ?: body["endToEndId"])?.toString()

        return AutoPayoutResponse(
            endToEndId       = endToEnd,
            transferId       = transferId,
            value            = body["valor"]?.toString(),
            payerKey         = (body["pagador"] as? Map<*, *>)?.get("chave")?.toString(),
            payerInfo        = (body["pagador"] as? Map<*, *>)?.get("infoPagador")?.toString(),
            status           = body["status"]?.toString(),
            requestedAt      = horario?.get("solicitacao")?.toString(),
            settledAt        = horario?.get("liquidacao")?.toString(),
            favoredKey       = favorecido?.get("chave")?.toString(),
            favoredName      = identificacao?.get("nome")?.toString(),
            favoredCpfMasked = identificacao?.get("cpf")?.toString(),
            favoredBankIspb  = contaBanco?.get("codigoBanco")?.toString(),
            raw              = body
        )
    }
}
