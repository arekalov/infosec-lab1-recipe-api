package ru.itmo.infosec.recipes.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.infosec.recipes.domain.UserAccount
import ru.itmo.infosec.recipes.repository.UserAccountRepository
import ru.itmo.infosec.recipes.security.LoginRateLimiter
import ru.itmo.infosec.recipes.web.dto.LoginRequest
import ru.itmo.infosec.recipes.web.dto.RegisterRequest
import ru.itmo.infosec.recipes.web.dto.TokenResponse
import ru.itmo.infosec.recipes.web.dto.UserResponse
import java.util.UUID

class UsernameAlreadyTakenException : RuntimeException("Username already taken")

class InvalidCredentialsException : RuntimeException("Invalid credentials")

class TooManyLoginAttemptsException : RuntimeException("Too many login attempts")

@Service
class AuthService(
    private val users: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val rateLimiter: LoginRateLimiter,
) {

    @Transactional
    fun register(request: RegisterRequest): UserResponse {
        val username = request.username.lowercase()
        if (users.existsByUsername(username)) throw UsernameAlreadyTakenException()

        // В БД уходит только bcrypt-хэш: соль генерируется внутри него, отдельного
        // поля под неё не нужно, а исходный пароль нигде не сохраняется и не логируется.
        val saved = users.save(
            UserAccount(username = username, passwordHash = hash(request.password)),
        )
        return saved.toResponse()
    }

    fun login(request: LoginRequest): TokenResponse {
        val username = request.username.lowercase()
        if (!rateLimiter.isAllowed(username)) throw TooManyLoginAttemptsException()

        val user = users.findByUsername(username)
        if (user == null) {
            // Пароль всё равно прогоняется через bcrypt по заглушке: без этого ответ
            // на несуществующий логин возвращался бы заметно быстрее, и по времени
            // ответа можно было бы собрать список существующих учёток.
            passwordEncoder.matches(request.password, dummyHash)
            rateLimiter.recordFailure(username)
            throw InvalidCredentialsException()
        }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            rateLimiter.recordFailure(username)
            throw InvalidCredentialsException()
        }

        rateLimiter.reset(username)
        return tokenService.issue(user)
    }

    private fun UserAccount.toResponse() = UserResponse(
        id = requireNotNull(id),
        username = username,
        roles = roles.toSet(),
        createdAt = createdAt,
    )

    /**
     * Хэш от случайной строки, ни с чем не совпадающий.
     * Считается один раз при первом обращении и живёт только в памяти процесса.
     */
    private val dummyHash: String by lazy { hash(UUID.randomUUID().toString()) }

    private fun hash(rawPassword: String): String =
        checkNotNull(passwordEncoder.encode(rawPassword)) { "Password encoder returned no hash" }
}
