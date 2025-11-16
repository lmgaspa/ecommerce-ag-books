package com.luizgasparetto.backend.monolito.infra.idle

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Component
@EnableScheduling
@EnableConfigurationProperties(IdleModeProperties::class)
class IdleStateService(
    private val props: IdleModeProperties
) {
    private val log = LoggerFactory.getLogger(IdleStateService::class.java)

    private val idle = AtomicBoolean(props.enabled)
    private val lastActivityRef = AtomicReference(Instant.now())

    fun isIdle(): Boolean = idle.get()

    fun markActivity() {
        lastActivityRef.set(Instant.now())
    }

    fun wakeIfConfigured(): Boolean {
        if (props.wakeOnFirstRequest && idle.get()) {
            idle.set(false)
            log.info("IdleMode: wake on first request → idle=false")
            return true
        }
        return false
    }

    fun activateIdle() {
        if (idle.compareAndSet(false, true)) {
            log.info("IdleMode: activated → idle=true")
        }
    }

    fun deactivateIdle() {
        if (idle.compareAndSet(true, false)) {
            log.info("IdleMode: deactivated → idle=false")
        }
    }

    fun lastActivity(): Instant = lastActivityRef.get()

    @Scheduled(fixedDelay = 60_000L)
    fun autoIdleByTimeout() {
        if (!props.enabled) return
        val since = Duration.between(lastActivityRef.get(), Instant.now()).toMinutes()
        if (!idle.get() && since >= props.timeoutMinutes) {
            log.info("IdleMode: no activity for {} minutes (>= {}), entering idle", since, props.timeoutMinutes)
            idle.set(true)
        }
    }
}


