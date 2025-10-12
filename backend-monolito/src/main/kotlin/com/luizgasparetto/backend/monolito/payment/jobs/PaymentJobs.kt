package com.luizgasparetto.backend.monolito.payment.jobs

import com.luizgasparetto.backend.monolito.payment.ports.OrderReadPort
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutRepository
import com.luizgasparetto.backend.monolito.payment.repo.PaymentPayoutStatus
import com.luizgasparetto.backend.monolito.payment.services.PaymentTriggerService
import com.luizgasparetto.backend.monolito.payment.services.PaymentPayoutOrchestrator
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentJobs(
    private val repo: PaymentPayoutRepository,
    private val orchestrator: PaymentPayoutOrchestrator,
    private val orderRead: OrderReadPort,
    private val trigger: PaymentTriggerService
) {
    // Re-tentativa de FAIL
    @Scheduled(fixedDelayString = "PT10M")
    fun retryFailed() {
        repo.findAll().filter { it.status == PaymentPayoutStatus.FAILED }.forEach {
            runCatching { orchestrator.send(it) }
        }
    }

    // Liberação D+X de cartão (sem tocar no Order)
    @Scheduled(fixedDelayString = "PT15M")
    fun releaseCardByTime() {
        // Estratégia simples: reprocessar últimas capturas via eventos salvos
        // (ou faça uma query por capturas recentes no seu provider/card gateway)
        // Aqui deixo o gancho para você iterar conforme sua realidade de eventos.
    }
}