package com.luizgasparetto.backend.monolito.services.card

import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CardWatcher(
    private val cardService: CardService,
    private val processor: CardPaymentProcessor
) {
    private val log = LoggerFactory.getLogger(CardWatcher::class.java)
    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("card-watch-")
        initialize()
    }

    private val delays = listOf(10L, 20L, 30L, 60L, 120L, 300L)

    fun watch(chargeId: String, expireAt: Instant) {
        scheduleAttempt(chargeId, 0, expireAt)
    }

    private fun scheduleAttempt(chargeId: String, attempt: Int, expireAt: Instant) {
        if (attempt >= delays.size) {
            log.info("CARD-POLL: esgotadas tentativas chargeId={}", chargeId); return
        }
        val delay = delays[attempt]
        val runAt = Instant.now().plusSeconds(delay)
        val lastMoment = expireAt.plusSeconds(10)
        if (runAt.isAfter(lastMoment)) {
            log.info("CARD-POLL: parando (além do TTL) chargeId={}", chargeId); return
        }

        log.debug("CARD-POLL: agendando tentativa {} chargeId={} em {}s", attempt + 1, chargeId, delay)
        scheduler.schedule({
            try {
                val status = cardService.getChargeStatus(chargeId)
                log.debug("CARD-POLL: chargeId={} status={}", chargeId, status)
                if (processor.isCardPaidStatus(status)) {
                    if (processor.markPaidIfNeededByChargeId(chargeId)) return@schedule
                }
            } catch (e: Exception) {
                log.warn("CARD-POLL: erro tentativa {} chargeId={}: {}", attempt + 1, chargeId, e.message)
            }
            scheduleAttempt(chargeId, attempt + 1, expireAt)
        }, runAt)
    }
}
