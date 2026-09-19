package ru.itmo.infosec.recipes.security

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Ограничитель попыток входа.
 *
 * Без него пара корректных мер (bcrypt, JWT) всё равно оставляла бы дверь открытой:
 * пароль можно было бы подбирать сколько угодно. Счётчик сбрасывается при успешном
 * входе и по истечении окна.
 *
 * Хранение в памяти достаточно для учебного одноузлового приложения; в распределённом
 * развёртывании счётчик нужно выносить в общее хранилище (Redis) — иначе лимит
 * обходится обращением к другому узлу.
 */
@Component
class LoginRateLimiter {

    private data class Attempts(val count: Int, val windowStart: Instant)

    private val attempts = ConcurrentHashMap<String, Attempts>()

    /** `true`, если попытка входа для этого ключа разрешена. */
    fun isAllowed(key: String): Boolean {
        val record = attempts[key] ?: return true
        if (isExpired(record)) {
            attempts.remove(key)
            return true
        }
        return record.count < MAX_ATTEMPTS
    }

    fun recordFailure(key: String) {
        pruneIfCrowded()
        attempts.compute(key) { _, current ->
            if (current == null || isExpired(current)) {
                Attempts(count = 1, windowStart = Instant.now())
            } else {
                current.copy(count = current.count + 1)
            }
        }
    }

    fun reset(key: String) {
        attempts.remove(key)
    }

    private fun isExpired(record: Attempts): Boolean =
        Duration.between(record.windowStart, Instant.now()) > WINDOW

    /**
     * Карта растёт от чужих логинов, поэтому просроченные записи периодически
     * вычищаются — иначе поток запросов со случайными именами исчерпал бы память.
     */
    private fun pruneIfCrowded() {
        if (attempts.size < PRUNE_THRESHOLD) return
        attempts.entries.removeIf { isExpired(it.value) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val PRUNE_THRESHOLD = 10_000
        val WINDOW: Duration = Duration.ofMinutes(5)
    }
}
