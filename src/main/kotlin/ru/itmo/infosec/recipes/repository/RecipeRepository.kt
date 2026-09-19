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
 *
 * Защита от IDOR: выборка всегда ограничена владельцем — нет метода, который достал бы
 * рецепт по одному лишь `id`, поэтому «забыть проверку» в сервисе технически невозможно.
 */
interface RecipeRepository : JpaRepository<Recipe, Long> {

    fun findByOwnerUsername(username: String, pageable: Pageable): Page<Recipe>

    fun findByIdAndOwnerUsername(id: Long, username: String): Recipe?

    fun deleteByIdAndOwnerUsername(id: Long, username: String): Long

    /**
     * Поиск по подстроке в названии среди рецептов пользователя.
     *
     * `:query` — bind-параметр, поэтому payload вида `' OR '1'='1` попадает в БД как
     * обычный текст и сравнивается с названием, а не меняет структуру запроса.
     */
    @Query(
        """
        SELECT r FROM Recipe r
        WHERE r.owner.username = :username
          AND LOWER(r.title) LIKE LOWER(CONCAT('%', :query, '%'))
        """,
    )
    fun search(
        @Param("username") username: String,
        @Param("query") query: String,
        pageable: Pageable,
    ): Page<Recipe>
}
