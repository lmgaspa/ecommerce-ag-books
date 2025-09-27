package com.luizgasparetto.backend.monolito.services.order

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture

@Service
class OrderEventsPublisher(
    private val taskScheduler: TaskScheduler
) {
    private val log = LoggerFactory.getLogger(OrderEventsPublisher::class.java)

    private val listeners = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()
    private val heartbeats = ConcurrentHashMap<SseEmitter, ScheduledFuture<*>>()

    fun subscribe(orderId: Long, timeoutMs: Long = 330_000L): SseEmitter {
        val emitter = SseEmitter(timeoutMs)
        val list = listeners.computeIfAbsent(orderId) { CopyOnWriteArrayList() }
        list += emitter
        log.info("SSE: subscribed orderId={}, listeners={}", orderId, list.size)

        safeSend(orderId, emitter, SseEmitter.event().name("open").data("ok"))

        val hb = taskScheduler.scheduleAtFixedRate(
            { safeSend(orderId, emitter, SseEmitter.event().name("ping").data("💓")) },
            Duration.ofSeconds(20)
        )
        // Em Kotlin o retorno não é nulo; guardar direto:
        heartbeats[emitter] = hb

        val onDone = { cleanup(orderId, emitter) }
        emitter.onCompletion(onDone)
        emitter.onTimeout(onDone)
        emitter.onError { onDone() }

        return emitter
    }

    fun publishPaid(orderId: Long) {
        listeners[orderId]?.let { subs ->
            var entregues = 0
            subs.forEach { em ->
                try {
                    em.send(SseEmitter.event().name("paid").data(mapOf("orderId" to orderId)))
                    entregues++
                    em.complete()
                } catch (ex: Exception) {
                    log.info("SSE: cliente desconectou ao enviar 'paid' (orderId={}): {}", orderId, ex.javaClass.simpleName)
                    cleanup(orderId, em)
                } finally {
                    heartbeats.remove(em)?.cancel(true)
                }
            }
            log.info("SSE: paid enviado orderId={}, entregues={}", orderId, entregues)
        }
    }

    private fun safeSend(orderId: Long, em: SseEmitter, ev: SseEmitter.SseEventBuilder) {
        try { em.send(ev) } catch (ex: Exception) {
            log.info("SSE: falha ao enviar evento, removendo emitter (orderId={}): {}", orderId, ex.javaClass.simpleName)
            cleanup(orderId, em)
        }
    }

    private fun cleanup(orderId: Long, emitter: SseEmitter) {
        heartbeats.remove(emitter)?.cancel(true)
        listeners[orderId]?.let { list ->
            list.remove(emitter)
            if (list.isEmpty()) listeners.remove(orderId)
            log.info("SSE: emitter closed orderId={}, remaining={}", orderId, list.size)
        }
        try { emitter.complete() } catch (_: Exception) {}
    }

    @PreDestroy
    fun shutdown() {
        heartbeats.values.forEach { it.cancel(true) }
        heartbeats.clear()
        listeners.values.flatten().forEach { try { it.complete() } catch (_: Exception) {} }
        listeners.clear()
    }
}
