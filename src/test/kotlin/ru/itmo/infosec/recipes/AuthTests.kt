package ru.itmo.infosec.recipes

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import ru.itmo.infosec.recipes.repository.UserAccountRepository
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Проверки аутентификации: хранение паролей, выдача токена и поведение
 * при неверных учётных данных.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val users: UserAccountRepository,
) {

    @Test
    fun `регистрация не возвращает и не хранит пароль в открытом виде`() {
        val username = uniqueUsername("store")
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(username, PASSWORD)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.username") { value(username) }
            jsonPath("$.passwordHash") { doesNotExist() }
            content { string(not(containsString(PASSWORD))) }
        }

        val stored = requireNotNull(users.findByUsername(username))
        assertNotEquals(PASSWORD, stored.passwordHash)
        assertTrue(stored.passwordHash.startsWith("\$2a\$12\$"), "ожидался bcrypt cost 12: ${stored.passwordHash}")
    }

    @Test
    fun `повторная регистрация того же логина отклоняется`() {
        val username = uniqueUsername("dup")
        register(mockMvc, username)
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(username, PASSWORD)
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `слишком короткий пароль не принимается`() {
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(uniqueUsername("short"), "short")
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `логин с недопустимыми символами не принимается`() {
        mockMvc.post("/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials("<script>alert(1)</script>", PASSWORD)
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `успешный вход выдаёт токен`() {
        val username = uniqueUsername("login")
        register(mockMvc, username)
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(username, PASSWORD)
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { exists() }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.expiresIn") { value(900) }
        }
    }

    /**
     * Ключевая проверка против перебора учёток: ответ на неверный пароль
     * не должен отличаться от ответа на несуществующий логин.
     */
    @Test
    fun `неверный пароль и несуществующий логин дают одинаковый ответ`() {
        val username = uniqueUsername("enum")
        register(mockMvc, username)

        val wrongPassword = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(username, "totally-wrong-password")
        }.andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString

        val unknownUser = mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(uniqueUsername("ghost"), "totally-wrong-password")
        }.andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString

        // `instance` совпадает, потому что путь один и тот же; сравниваем ответы целиком.
        assertTrue(wrongPassword == unknownUser, "ответы различаются:\n$wrongPassword\n$unknownUser")
    }

    @Test
    fun `после пяти неудачных попыток вход временно блокируется`() {
        val username = uniqueUsername("brute")
        register(mockMvc, username)
        repeat(5) {
            mockMvc.post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = credentials(username, "wrong-password-attempt")
            }.andExpect { status { isUnauthorized() } }
        }
        mockMvc.post("/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = credentials(username, PASSWORD)
        }.andExpect { status { isTooManyRequests() } }
    }
}
