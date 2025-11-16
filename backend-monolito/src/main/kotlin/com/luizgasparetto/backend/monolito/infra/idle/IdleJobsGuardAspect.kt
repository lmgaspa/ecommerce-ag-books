package com.luizgasparetto.backend.monolito.infra.idle

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Scheduled

@Aspect
@Component
@Order(1) // run early
class IdleJobsGuardAspect(
    private val idle: IdleStateService
) {
    private val log = LoggerFactory.getLogger(IdleJobsGuardAspect::class.java)

    // Guard any @Scheduled method
    @Around("@annotation(scheduled)")
    @Throws(Throwable::class)
    fun aroundScheduled(joinPoint: ProceedingJoinPoint, scheduled: Scheduled): Any? {
        if (idle.isIdle()) {
            // Keep logs quiet when idle; only trace
            if (log.isTraceEnabled) {
                log.trace("IdleMode: skipping scheduled execution {}", signatureOf(joinPoint))
            }
            return null
        }
        return joinPoint.proceed()
    }

    // Guard known job classes by package/name (Reaper/Invalidator)
    @Around("execution(* com.luizgasparetto.backend.monolito..*(..)) && (" +
            "within(com.luizgasparetto.backend.monolito..jobs..*) || " +
            "within(com.luizgasparetto.backend.monolito..services.card..*) || " +
            "within(com.luizgasparetto.backend.monolito..services.pix..*)" +
            ") && (execution(* *Reaper.*(..)) || execution(* *Invalidator.*(..)))")
    @Throws(Throwable::class)
    fun aroundKnownJobs(joinPoint: ProceedingJoinPoint): Any? {
        if (idle.isIdle()) {
            if (log.isTraceEnabled) {
                log.trace("IdleMode: skipping job {}", signatureOf(joinPoint))
            }
            return null
        }
        return joinPoint.proceed()
    }

    private fun signatureOf(pjp: ProceedingJoinPoint): String =
        pjp.signature.toShortString()
}


