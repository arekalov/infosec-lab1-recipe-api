package ru.itmo.infosec.recipes

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.assertTrue

/**
 * Функциональные проверки CRUD и защиты данных: инъекции, XSS и доступ к чужим записям.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecipeApiTests(@Autowired private val mockMvc: MockMvc) {

    @Test
    fun `полный цикл создание-чтение-обновление-удаление`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("crud"))
        val id = createRecipe(token, BORSCHT)

        mockMvc.get("/api/recipes/$id") { auth(token) }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Борщ") }
            jsonPath("$.ingredients.length()") { value(3) }
        }

        mockMvc.put("/api/recipes/$id") {
            auth(token)
            contentType = MediaType.APPLICATION_JSON
            content = recipeJson(title = "Борщ украинский", cookMinutes = 90)
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Борщ украинский") }
            jsonPath("$.cookMinutes") { value(90) }
        }

        mockMvc.delete("/api/recipes/$id") { auth(token) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/recipes/$id") { auth(token) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `некорректное тело запроса отклоняется с разбором по полям`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("validation"))
        mockMvc.post("/api/recipes") {
            auth(token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"","description":"","ingredients":[],"instructions":"","cookMinutes":0,"servings":0}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors.title") { exists() }
            jsonPath("$.errors.ingredients") { exists() }
            jsonPath("$.errors.cookMinutes") { exists() }
        }
    }

    @Test
    fun `payload SQL-инъекции обрабатывается как обычный текст`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("sqli"))
        createRecipe(token, BORSCHT)

        // Классический обход условия: если бы строка склеивалась с SQL,
        // запрос вернул бы все записи.
        mockMvc.get("/api/data") {
            auth(token)
            param("q", "' OR '1'='1")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItems") { value(0) }
        }

        // Попытка уронить таблицу.
        mockMvc.get("/api/data") {
            auth(token)
            param("q", "'; DROP TABLE recipes;--")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItems") { value(0) }
        }

        // Таблица на месте, данные целы.
        mockMvc.get("/api/data") { auth(token) }.andExpect {
            status { isOk() }
            jsonPath("$.totalItems") { value(1) }
        }
    }

    @Test
    fun `поиск по настоящей подстроке продолжает работать`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("search"))
        createRecipe(token, BORSCHT)
        mockMvc.get("/api/data") {
            auth(token)
            param("q", "борщ")
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalItems") { value(1) }
        }
    }

    @Test
    fun `разметка в пользовательском тексте возвращается экранированной`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("xss"))
        val id = createRecipe(
            token,
            recipeJson(
                title = "<script>alert(1)</script>",
                description = "<img src=x onerror=alert(1)>",
            ),
        )

        val body = mockMvc.get("/api/recipes/$id") { auth(token) }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("&lt;script&gt;alert(1)&lt;/script&gt;") }
            // Текст `onerror=alert(1)` в значении остаётся, и это правильно: он безопасен
            // ровно потому, что открывающая и закрывающая скобки тега экранированы и
            // браузер никогда не разберёт это как элемент с обработчиком события.
            jsonPath("$.description") { value("&lt;img src=x onerror=alert(1)&gt;") }
        }.andReturn().response.contentAsString

        // Главный инвариант: в ответе не осталось ни одной неэкранированной угловой скобки.
        assertTrue('<' !in body && '>' !in body, "в ответе осталась сырая разметка: $body")
    }

    @Test
    fun `чужой рецепт неотличим от несуществующего`() {
        val ownerToken = registerAndLogin(mockMvc, uniqueUsername("owner"))
        val id = createRecipe(ownerToken, BORSCHT)
        val strangerToken = registerAndLogin(mockMvc, uniqueUsername("stranger"))

        mockMvc.get("/api/recipes/$id") { auth(strangerToken) }.andExpect { status { isNotFound() } }
        mockMvc.put("/api/recipes/$id") {
            auth(strangerToken)
            contentType = MediaType.APPLICATION_JSON
            content = recipeJson(title = "Взломано")
        }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/recipes/$id") { auth(strangerToken) }.andExpect { status { isNotFound() } }

        // Владелец по-прежнему видит рецепт нетронутым.
        mockMvc.get("/api/recipes/$id") { auth(ownerToken) }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Борщ") }
        }
    }

    @Test
    fun `в списке видны только свои рецепты`() {
        val first = registerAndLogin(mockMvc, uniqueUsername("list-a"))
        val second = registerAndLogin(mockMvc, uniqueUsername("list-b"))
        createRecipe(first, BORSCHT)

        mockMvc.get("/api/data") { auth(first) }.andExpect { jsonPath("$.totalItems") { value(1) } }
        mockMvc.get("/api/data") { auth(second) }.andExpect { jsonPath("$.totalItems") { value(0) } }
    }

    @Test
    fun `размер страницы ограничен сверху`() {
        val token = registerAndLogin(mockMvc, uniqueUsername("paging"))
        mockMvc.get("/api/data") {
            auth(token)
            param("size", "100000")
        }.andExpect {
            status { isOk() }
            jsonPath("$.size") { value(100) }
        }
    }

    private fun createRecipe(token: String, json: String): Long {
        val response = mockMvc.post("/api/recipes") {
            auth(token)
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        return Regex("\"id\"\\s*:\\s*(\\d+)").find(response)?.groupValues?.get(1)?.toLong()
            ?: error("В ответе нет id: $response")
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.auth(token: String) {
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
    }

    private companion object {
        val BORSCHT = recipeJson()

        fun recipeJson(
            title: String = "Борщ",
            description: String = "Классический борщ",
            cookMinutes: Int = 120,
        ): String = """
            {
              "title": ${title.jsonString()},
              "description": ${description.jsonString()},
              "ingredients": ["свёкла", "капуста", "говядина"],
              "instructions": "Сварить бульон, добавить овощи, тушить 40 минут.",
              "cookMinutes": $cookMinutes,
              "servings": 6
            }
        """.trimIndent()

        private fun String.jsonString(): String =
            "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
