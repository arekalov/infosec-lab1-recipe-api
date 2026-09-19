package ru.itmo.infosec.recipes.web

import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.itmo.infosec.recipes.service.RecipeService
import ru.itmo.infosec.recipes.web.dto.PageResponse
import ru.itmo.infosec.recipes.web.dto.RecipeRequest
import ru.itmo.infosec.recipes.web.dto.RecipeResponse
import java.net.URI

/**
 * Рецепты текущего пользователя. Все маршруты закрыты JWT-фильтром.
 *
 * Имя пользователя берётся из проверенного токена ([AuthenticationPrincipal]), а не из
 * параметра запроса или заголовка: иначе клиент мог бы назваться кем угодно.
 */
@RestController
class RecipeController(private val service: RecipeService) {

    /**
     * `GET /api/data` — эндпоинт, который требует методичка.
     * Постраничный список рецептов пользователя с необязательным поиском по названию.
     */
    @GetMapping("/api/data")
    fun data(
        @AuthenticationPrincipal username: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResponse<RecipeResponse> {
        // Размер страницы ограничен сверху: без этого клиент мог бы запросить
        // Int.MAX_VALUE записей и исчерпать память приложения.
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt"),
        )
        return service.list(username, q, pageable).toResponse()
    }

    @PostMapping("/api/recipes")
    fun create(
        @AuthenticationPrincipal username: String,
        @Valid @RequestBody request: RecipeRequest,
    ): ResponseEntity<RecipeResponse> {
        val created = service.create(username, request).toResponse()
        return ResponseEntity.created(URI.create("/api/recipes/${created.id}")).body(created)
    }

    @GetMapping("/api/recipes/{id}")
    fun get(
        @AuthenticationPrincipal username: String,
        @PathVariable id: Long,
    ): RecipeResponse = service.get(username, id).toResponse()

    @PutMapping("/api/recipes/{id}")
    fun update(
        @AuthenticationPrincipal username: String,
        @PathVariable id: Long,
        @Valid @RequestBody request: RecipeRequest,
    ): RecipeResponse = service.update(username, id, request).toResponse()

    @DeleteMapping("/api/recipes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @AuthenticationPrincipal username: String,
        @PathVariable id: Long,
    ) = service.delete(username, id)

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
