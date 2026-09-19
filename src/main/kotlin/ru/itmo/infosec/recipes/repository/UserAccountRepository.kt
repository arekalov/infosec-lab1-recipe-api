package ru.itmo.infosec.recipes.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.infosec.recipes.domain.UserAccount

interface UserAccountRepository : JpaRepository<UserAccount, Long> {

    /** Derived query: имя подставляется как bind-параметр, конкатенации SQL нет. */
    fun findByUsername(username: String): UserAccount?

    fun existsByUsername(username: String): Boolean
}
