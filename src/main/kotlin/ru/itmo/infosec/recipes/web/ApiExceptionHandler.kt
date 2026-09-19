package ru.itmo.infosec.recipes.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import ru.itmo.infosec.recipes.service.InvalidCredentialsException
import ru.itmo.infosec.recipes.service.RecipeNotFoundException
import ru.itmo.infosec.recipes.service.TooManyLoginAttemptsException
import ru.itmo.infosec.recipes.service.UsernameAlreadyTakenException

/**
 * Единая обработка ошибок в формате RFC 7807 (`application/problem+json`).
 *
 * Принцип: наружу уходит ровно столько, сколько нужно клиенту, чтобы исправить запрос.
 * Текст непредвиденного исключения пишется в лог, а в ответе остаётся обезличенное
 * сообщение — иначе стектрейс и имена классов подсказали бы атакующему устройство системы.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(RecipeNotFoundException::class)
    fun handleNotFound(ex: RecipeNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Recipe not found")

    /**
     * Один и тот же ответ и на неизвестный логин, и на неверный пароль:
     * различать их значило бы выдавать список существующих учётных записей.
     */
    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "Invalid username or password")

    @ExceptionHandler(TooManyLoginAttemptsException::class)
    fun handleTooManyAttempts(ex: TooManyLoginAttemptsException): ProblemDetail =
        problem(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts, try again later")

    @ExceptionHandler(UsernameAlreadyTakenException::class)
    fun handleUsernameTaken(ex: UsernameAlreadyTakenException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Username already taken")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ProblemDetail =
        problem(HttpStatus.UNAUTHORIZED, "Authentication required")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ProblemDetail =
        problem(HttpStatus.FORBIDDEN, "Access denied")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error")
    }

    /**
     * Ошибки валидации — единственный случай, когда мы отдаём подробности:
     * это данные самого клиента, ничего внутреннего они не раскрывают.
     */
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> {
        val problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed").apply {
            setProperty(
                "errors",
                ex.bindingResult.fieldErrors
                    .groupBy { it.field }
                    .mapValues { (_, errors) -> errors.mapNotNull { it.defaultMessage }.sorted() },
            )
        }
        return ResponseEntity.badRequest().body(problem)
    }

    private fun problem(status: HttpStatus, detail: String): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply { title = status.reasonPhrase }
}
