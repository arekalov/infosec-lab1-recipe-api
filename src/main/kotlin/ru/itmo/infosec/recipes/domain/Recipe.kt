package ru.itmo.infosec.recipes.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant

/**
 * Рецепт — основная сущность предметной области.
 *
 * Намеренно обычный `class`, а не `data class`: сгенерированные `equals`/`hashCode`
 * учитывали бы изменяемый `id` и ленивые коллекции, что ломает поведение сущности
 * внутри persistence context и в `Set`.
 */
@Entity
@Table(name = "recipes")
class Recipe(
    @Column(nullable = false, length = TITLE_MAX)
    var title: String,

    @Column(nullable = false, length = DESCRIPTION_MAX)
    var description: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipe_ingredients", joinColumns = [JoinColumn(name = "recipe_id")])
    @Column(name = "ingredient", nullable = false, length = INGREDIENT_MAX)
    var ingredients: MutableList<String>,

    @Column(nullable = false, length = INSTRUCTIONS_MAX)
    var instructions: String,

    @Column(name = "cook_minutes", nullable = false)
    var cookMinutes: Int,

    @Column(nullable = false)
    var servings: Int,

    /**
     * Владелец рецепта. Читать и менять рецепт может только он —
     * это защита от IDOR (OWASP A01: Broken Access Control).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: UserAccount,

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
) {
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    companion object {
        const val TITLE_MAX = 120
        const val DESCRIPTION_MAX = 2000
        const val INGREDIENT_MAX = 200
        const val INSTRUCTIONS_MAX = 5000
        const val INGREDIENTS_MAX_COUNT = 50

        // Long, потому что jakarta.validation.constraints.Max принимает long.
        const val COOK_MINUTES_MAX = 1440L
        const val SERVINGS_MAX = 100L
    }
}
