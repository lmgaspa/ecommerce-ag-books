// src/main/kotlin/.../services/payout/PayoutSendService.kt
package com.luizgasparetto.backend.monolito.services.payout

import com.luizgasparetto.backend.monolito.api.payout.*
import com.luizgasparetto.backend.monolito.config.EfiPayoutProps
import com.luizgasparetto.backend.monolito.efi.PixSendClient
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.random.Random

@Service
class PayoutSendService(
    private val calc: PayoutCalculator,
    private val props: EfiPayoutProps,
    private val pix: PixSendClient
) {
    private fun genIdEnvio(): String {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..26).map { alpha[Random.nextInt(alpha.length)] }.joinToString("")
    }

    fun send(req: PayoutSendReq): PayoutSendRes {
        val prev = calc.preview(req.preview)
        require(prev.canSend) { "Valor líquido abaixo do mínimo (minSend=${prev.minSend})." }

        val id = req.idEnvio ?: genIdEnvio()
        val dest = req.favoredKeyOverride ?: props.favoredKey
        ?: error("efi.payout.favored-key não configurado")

        val res = pix.enviar(
            idEnvio = id,
            valor = prev.net,                       // envia o líquido
            favorecidoChave = dest,
            info = req.infoPagador
        )

        return PayoutSendRes(
            preview = prev,
            idEnvio = id,
            status = res.status ?: "ENVIADO",
            endToEndId = res.endToEndId
        )
    }
}
