package ru.itmo.infosec.recipes.web

import org.springframework.data.domain.Page
import ru.itmo.infosec.recipes.domain.Recipe
import ru.itmo.infosec.recipes.web.dto.PageResponse
import ru.itmo.infosec.recipes.web.dto.RecipeResponse

/**
 * Единственное место, где сущность превращается в ответ API.
 *
 * Именно здесь на 2-м этапе появится экранирование пользовательского текста —
 * держать это в одной точке проще, чем размазывать по контроллерам.
 */
fun Recipe.toResponse(): RecipeResponse = RecipeResponse(
    id = requireNotNull(id) { "Recipe must be persisted before it is returned" },
    title = title,
    description = description,
    ingredients = ingredients.toList(),
    instructions = instructions,
    cookMinutes = cookMinutes,
    servings = servings,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Page<Recipe>.toResponse(): PageResponse<RecipeResponse> = PageResponse(
    items = content.map { it.toResponse() },
    page = number,
    size = size,
    totalItems = totalElements,
    totalPages = totalPages,
)
