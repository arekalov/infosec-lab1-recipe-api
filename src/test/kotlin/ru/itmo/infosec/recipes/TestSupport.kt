package ru.itmo.infosec.recipes

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.concurrent.atomic.AtomicLong

/** Пароль, удовлетворяющий политике (минимум 12 символов). */
const val PASSWORD = "correct-horse-battery-staple"

private val counter = AtomicLong()

/**
 * Уникальное имя пользователя на каждый вызов.
 *
 * Тесты делят одну in-memory базу и один экземпляр ограничителя попыток входа,
 * поэтому пересечение по логинам сделало бы их зависимыми от порядка запуска.
 */
fun uniqueUsername(prefix: String): String = "${prefix}_${counter.incrementAndGet()}"

fun credentials(username: String, password: String): String =
    """{"username":"$username","password":"$password"}"""

/** Регистрирует пользователя и возвращает его имя. */
fun register(mockMvc: MockMvc, username: String, password: String = PASSWORD): String {
    mockMvc.post("/auth/register") {
        contentType = MediaType.APPLICATION_JSON
        content = credentials(username, password)
    }.andExpect { status { isCreated() } }
    return username
}

/** Регистрирует пользователя и возвращает его access-токен. */
fun registerAndLogin(mockMvc: MockMvc, username: String, password: String = PASSWORD): String {
    register(mockMvc, username, password)
    val response = mockMvc.post("/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = credentials(username, password)
    }.andExpect { status { isOk() } }.andReturn().response.contentAsString

    return Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(response)
        ?.groupValues?.get(1)
        ?: error("В ответе на /auth/login нет accessToken: $response")
}
