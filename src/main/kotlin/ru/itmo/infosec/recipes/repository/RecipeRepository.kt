package ru.itmo.infosec.recipes.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.itmo.infosec.recipes.domain.Recipe

/**
 * Доступ к рецептам.
 *
 * Защита от SQL-инъекций: все запросы либо derived (Spring Data строит их из имени метода),
 * либо JPQL с именованными параметрами. Пользовательский ввод всегда попадает в запрос
 * как bind-переменная и никогда не склеивается со строкой запроса.
 */
interface RecipeRepository : JpaRepository<Recipe, Long> {

    /**
     * Поиск по подстроке в названии. `:query` — bind-параметр, поэтому payload вида
     * `' OR '1'='1` сравнивается как обычный текст и не меняет структуру запроса.
     */
    @Query(
        """
        SELECT r FROM Recipe r
        WHERE LOWER(r.title) LIKE LOWER(CONCAT('%', :query, '%'))
        """,
    )
    fun search(@Param("query") query: String, pageable: Pageable): Page<Recipe>
}
