package com.luizgasparetto.backend.monolito.services.pix

import org.slf4j.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PixWatcher(
    private val pixClient: PixClient,
    private val processor: PixPaymentProcessor
) {
    private val log = LoggerFactory.getLogger(PixWatcher::class.java)
    private val scheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("pix-watch-")
        initialize()
    }

    // Tentativas (s): 10, 20, 30, 60, 120, 300
    private val delays = listOf(10L, 20L, 30L, 60L, 120L, 300L)

    fun watch(txid: String, expireAt: Instant) {
        scheduleAttempt(txid, 0, expireAt)
    }

    private fun scheduleAttempt(txid: String, attempt: Int, expireAt: Instant) {
        if (attempt >= delays.size) {
            log.info("POLL: esgotadas tentativas para txid={}", txid); return
        }
        val delay = delays[attempt]
        val runAt = Instant.now().plusSeconds(delay)

        // Não agenda além do TTL (+10s de folga)
        val lastMoment = expireAt.plusSeconds(10)
        if (runAt.isAfter(lastMoment)) {
            log.info("POLL: parando (além do TTL) txid={}", txid); return
        }

        log.info("POLL: agendando tentativa {} para txid={} em {}s", attempt + 1, txid, delay)
        scheduler.schedule({
            try {
                val status = pixClient.status(txid)
                log.info("POLL: txid={} status={}", txid, status)
                if (processor.isPaidStatus(status)) {
                    if (processor.markPaidIfNeededByTxid(txid)) return@schedule
                }
            } catch (e: Exception) {
                log.warn("POLL: erro na tentativa {} txid={}: {}", attempt + 1, txid, e.message)
            }
            scheduleAttempt(txid, attempt + 1, expireAt)
        }, runAt)
    }
}

//