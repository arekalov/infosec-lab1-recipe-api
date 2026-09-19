package ru.itmo.infosec.recipes.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Настройки подписи JWT.
 *
 * Секрет приходит только из окружения (`JWT_SECRET`) и никогда не лежит в репозитории.
 * Длина проверяется на старте: для HS256 ключ короче 256 бит делает подпись подбираемой,
 * и лучше не запуститься вовсе, чем работать с заведомо слабым ключом.
 */
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    /** Base64-строка, минимум 32 байта после декодирования. */
    val secret: String,
    val issuer: String = "recipe-api",
    val ttl: Duration = Duration.ofMinutes(15),
) {
    fun secretKey(): SecretKey {
        val raw = runCatching { Base64.getDecoder().decode(secret) }
            .getOrElse { error("app.jwt.secret must be valid Base64") }
        require(raw.size >= MIN_SECRET_BYTES) {
            "app.jwt.secret must decode to at least $MIN_SECRET_BYTES bytes, got ${raw.size}"
        }
        return SecretKeySpec(raw, "HmacSHA256")
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
