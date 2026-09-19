package ru.itmo.infosec.recipes.web.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import ru.itmo.infosec.recipes.domain.Recipe
import java.time.Instant

/**
 * Тело запроса на создание/обновление рецепта.
 *
 * Отдельный DTO вместо приёма сущности напрямую — это защита от mass assignment:
 * клиент физически не может прислать `id`, `createdAt` или (начиная со 2-го этапа)
 * `owner` и переписать их.
 *
 * Ограничения Jakarta Validation — первая линия обороны: слишком длинный или пустой
 * ввод отсекается до того, как дойдёт до БД и до сериализации в ответ.
 */
data class RecipeRequest(
    @field:NotBlank
    @field:Size(min = 3, max = Recipe.TITLE_MAX)
    val title: String,

    @field:NotBlank
    @field:Size(max = Recipe.DESCRIPTION_MAX)
    val description: String,

    @field:NotEmpty
    @field:Size(max = Recipe.INGREDIENTS_MAX_COUNT)
    val ingredients: List<@NotBlank @Size(max = Recipe.INGREDIENT_MAX) String>,

    @field:NotBlank
    @field:Size(max = Recipe.INSTRUCTIONS_MAX)
    val instructions: String,

    @field:Min(1)
    @field:Max(Recipe.COOK_MINUTES_MAX)
    val cookMinutes: Int,

    @field:Min(1)
    @field:Max(Recipe.SERVINGS_MAX)
    val servings: Int,
)

/** Представление рецепта в ответе API. */
data class RecipeResponse(
    val id: Long,
    val title: String,
    val description: String,
    val ingredients: List<String>,
    val instructions: String,
    val cookMinutes: Int,
    val servings: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Страница результатов.
 *
 * Свой DTO вместо отдачи `Page` наружу: JSON-представление `PageImpl` не является
 * частью публичного контракта Spring Data и может меняться между версиями.
 */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int,
)
