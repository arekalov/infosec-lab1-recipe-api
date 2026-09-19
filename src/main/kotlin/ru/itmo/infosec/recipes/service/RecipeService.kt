package ru.itmo.infosec.recipes.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.infosec.recipes.domain.Recipe
import ru.itmo.infosec.recipes.repository.RecipeRepository
import ru.itmo.infosec.recipes.repository.UserAccountRepository
import ru.itmo.infosec.recipes.web.dto.RecipeRequest

/** Рецепт не найден — либо его нет, либо он принадлежит другому пользователю. */
class RecipeNotFoundException(id: Long) : RuntimeException("Recipe $id not found")

/**
 * Операции над рецептами.
 *
 * Каждый метод принимает имя текущего пользователя и передаёт его в запрос к БД.
 * Рецепт чужого пользователя неотличим от несуществующего — ответ в обоих случаях 404,
 * поэтому перебором идентификаторов нельзя выяснить, какие рецепты вообще существуют.
 */
@Service
@Transactional(readOnly = true)
class RecipeService(
    private val recipes: RecipeRepository,
    private val users: UserAccountRepository,
) {

    fun list(username: String, query: String?, pageable: Pageable): Page<Recipe> =
        if (query.isNullOrBlank()) {
            recipes.findByOwnerUsername(username, pageable)
        } else {
            recipes.search(username, query, pageable)
        }

    fun get(username: String, id: Long): Recipe =
        recipes.findByIdAndOwnerUsername(id, username) ?: throw RecipeNotFoundException(id)

    @Transactional
    fun create(username: String, request: RecipeRequest): Recipe {
        val owner = users.findByUsername(username)
            ?: error("Authenticated user '$username' is missing from the database")
        return recipes.save(
            Recipe(
                title = request.title,
                description = request.description,
                ingredients = request.ingredients.toMutableList(),
                instructions = request.instructions,
                cookMinutes = request.cookMinutes,
                servings = request.servings,
                owner = owner,
            ),
        )
    }

    @Transactional
    fun update(username: String, id: Long, request: RecipeRequest): Recipe {
        val recipe = get(username, id)
        recipe.title = request.title
        recipe.description = request.description
        recipe.ingredients = request.ingredients.toMutableList()
        recipe.instructions = request.instructions
        recipe.cookMinutes = request.cookMinutes
        recipe.servings = request.servings
        return recipes.save(recipe)
    }

    @Transactional
    fun delete(username: String, id: Long) {
        if (recipes.deleteByIdAndOwnerUsername(id, username) == 0L) throw RecipeNotFoundException(id)
    }
}
