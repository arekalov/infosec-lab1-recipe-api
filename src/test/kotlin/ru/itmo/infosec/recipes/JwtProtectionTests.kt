package ru.itmo.infosec.recipes

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.spec.SecretKeySpec

/**
 * Проверки JWT-фильтра: какие токены он принимает, а какие — нет.
 *
 * Токены для негативных сценариев собираются здесь же настоящим энкодером,
 * чтобы отличались ровно одним свойством (ключ, срок, subject), а не формой.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtProtectionTests(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `без токена доступ закрыт`() {
        mockMvc.get("/api/data").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `с валидным токеном доступ открыт`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("jwt"))
        mockMvc.get("/api/data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `строка, не являющаяся токеном, отклоняется`() {
        mockMvc.get("/api/data") { header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `токен, подписанный чужим ключом, отклоняется`() {
        val forged = encodeWith(FOREIGN_KEY) {
            it.subject("attacker").expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
        }
        mockMvc.get("/api/data") { header(HttpHeaders.AUTHORIZATION, "Bearer $forged") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `просроченный токен отклоняется`() {
        val expired = encodeWith(TEST_KEY) {
            val past = Instant.now().minus(2, ChronoUnit.HOURS)
            it.subject("someone").issuedAt(past).expiresAt(past.plus(1, ChronoUnit.MINUTES))
        }
        mockMvc.get("/api/data") { header(HttpHeaders.AUTHORIZATION, "Bearer $expired") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `ответ содержит заголовки, ограничивающие поведение браузера`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("headers"))
        mockMvc.get("/api/data") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect {
                status { isOk() }
                header { string("X-Content-Type-Options", "nosniff") }
                header { string("X-Frame-Options", "DENY") }
                header { exists("Content-Security-Policy") }
            }
    }

    private fun encodeWith(key: String, customize: (JwtClaimsSet.Builder) -> Unit): String {
        val secretKey = SecretKeySpec(java.util.Base64.getDecoder().decode(key), "HmacSHA256")
        val encoder = NimbusJwtEncoder.withSecretKey(secretKey).algorithm(MacAlgorithm.HS256).build()
        val claims = JwtClaimsSet.builder()
            .issuer("recipe-api-test")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
            .claim("roles", listOf("USER"))
            .also(customize)
            .build()
        return encoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    private companion object {
        /** Тот же ключ, что в `src/test/resources/application.yml`. */
        const val TEST_KEY = "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLXVuaXQtdGVzdHMh"

        /** Ключ «злоумышленника» — валидный по длине, но приложению неизвестный. */
        const val FOREIGN_KEY = "YXR0YWNrZXItY29udHJvbGxlZC1rZXktMzItYnl0ZXMtbG9uZyE="
    }
}
