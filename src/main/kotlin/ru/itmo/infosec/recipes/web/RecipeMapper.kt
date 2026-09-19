package ru.itmo.infosec.recipes.web

import org.owasp.encoder.Encode
import org.springframework.data.domain.Page
import ru.itmo.infosec.recipes.domain.Recipe
import ru.itmo.infosec.recipes.web.dto.PageResponse
import ru.itmo.infosec.recipes.web.dto.RecipeResponse

/**
 * Единственное место, где сущность превращается в ответ API.
 *
 * Здесь же — защита от XSS. Текст пользователя хранится в БД как есть, а экранируется
 * на выходе: так ничего не теряется при сохранении, но любой потребитель ответа получает
 * данные, безопасные для вставки в HTML. Экранирование именно в одной точке, а не в
 * контроллерах, гарантирует, что новый эндпоинт не отдаст сырой текст по недосмотру.
 *
 * `<script>alert(1)</script>` в названии вернётся как
 * `&lt;script&gt;alert(1)&lt;/script&gt;` и не выполнится, даже если фронтенд вставит
 * значение через `innerHTML`.
 */
fun Recipe.toResponse(): RecipeResponse = RecipeResponse(
    id = requireNotNull(id) { "Recipe must be persisted before it is returned" },
    title = Encode.forHtml(title),
    description = Encode.forHtml(description),
    ingredients = ingredients.map(Encode::forHtml),
    instructions = Encode.forHtml(instructions),
    // Числа и метки времени не несут пользовательского текста — экранировать нечего.
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
