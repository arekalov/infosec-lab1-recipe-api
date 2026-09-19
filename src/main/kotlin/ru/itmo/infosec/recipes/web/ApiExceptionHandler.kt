package ru.itmo.infosec.recipes.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import ru.itmo.infosec.recipes.service.RecipeNotFoundException

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
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recipe not found").apply {
            title = "Not Found"
        }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error").apply {
            title = "Internal Server Error"
        }
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
    ): ResponseEntity<Any>? {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed").apply {
            title = "Bad Request"
            setProperty(
                "errors",
                ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") },
            )
        }
        return ResponseEntity.badRequest().body(problem)
    }
}
