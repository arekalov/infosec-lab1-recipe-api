package ru.itmo.infosec.recipes.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.infosec.recipes.domain.Recipe
import ru.itmo.infosec.recipes.repository.RecipeRepository
import ru.itmo.infosec.recipes.web.dto.RecipeRequest

/** Рецепт с указанным идентификатором не найден. */
class RecipeNotFoundException(id: Long) : RuntimeException("Recipe $id not found")

@Service
@Transactional(readOnly = true)
class RecipeService(private val repository: RecipeRepository) {

    fun list(query: String?, pageable: Pageable): Page<Recipe> =
        if (query.isNullOrBlank()) repository.findAll(pageable) else repository.search(query, pageable)

    fun get(id: Long): Recipe = repository.findById(id).orElseThrow { RecipeNotFoundException(id) }

    @Transactional
    fun create(request: RecipeRequest): Recipe = repository.save(
        Recipe(
            title = request.title,
            description = request.description,
            ingredients = request.ingredients.toMutableList(),
            instructions = request.instructions,
            cookMinutes = request.cookMinutes,
            servings = request.servings,
        ),
    )

    @Transactional
    fun update(id: Long, request: RecipeRequest): Recipe {
        val recipe = get(id)
        recipe.title = request.title
        recipe.description = request.description
        recipe.ingredients = request.ingredients.toMutableList()
        recipe.instructions = request.instructions
        recipe.cookMinutes = request.cookMinutes
        recipe.servings = request.servings
        return repository.save(recipe)
    }

    @Transactional
    fun delete(id: Long) {
        if (!repository.existsById(id)) throw RecipeNotFoundException(id)
        repository.deleteById(id)
    }
}
