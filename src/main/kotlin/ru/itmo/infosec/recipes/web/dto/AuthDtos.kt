package ru.itmo.infosec.recipes.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import ru.itmo.infosec.recipes.domain.UserAccount
import java.time.Instant

data class RegisterRequest(
    @field:Pattern(regexp = UserAccount.USERNAME_PATTERN, message = "must contain only letters, digits, . _ -")
    @field:Size(min = UserAccount.USERNAME_MIN, max = UserAccount.USERNAME_MAX)
    val username: String,

    /**
     * Минимум 12 символов. Длина — единственное требование: навязывание спецсимволов
     * подталкивает пользователей к предсказуемым заменам вроде `Password1!`,
     * тогда как длина растит энтропию линейно (рекомендация OWASP ASVS).
     */
    @field:Size(min = UserAccount.PASSWORD_MIN, max = UserAccount.PASSWORD_MAX)
    val password: String,
)

/**
 * На входе никаких `@Pattern`/`@Size`: сообщение о нарушении формата подсказало бы
 * атакующему политику паролей и отличало бы «такого логина не бывает» от «пароль неверен».
 */
data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    /** Время жизни токена в секундах. */
    val expiresIn: Long,
)

/** Публичное представление учётной записи. Хэша пароля здесь нет и быть не может. */
data class UserResponse(
    val id: Long,
    val username: String,
    val roles: Set<String>,
    val createdAt: Instant,
)
