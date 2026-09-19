package ru.itmo.infosec.recipes.service

import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import ru.itmo.infosec.recipes.config.JwtProperties
import ru.itmo.infosec.recipes.domain.UserAccount
import ru.itmo.infosec.recipes.security.JwtAuthFilter
import ru.itmo.infosec.recipes.web.dto.TokenResponse
import java.time.Instant

/**
 * Выпуск access-токенов.
 *
 * В токен кладётся только то, что нужно для авторизации: кто (`sub`), с какими ролями,
 * кем выпущен и до какого момента действителен. Ничего чувствительного — содержимое JWT
 * не шифруется, а лишь подписывается, и любой владелец токена может его прочитать.
 */
@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val properties: JwtProperties,
) {
    fun issue(user: UserAccount): TokenResponse {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(user.username)
            .issuedAt(now)
            .expiresAt(now.plus(properties.ttl))
            .claim(JwtAuthFilter.CLAIM_ROLES, user.roles.toList())
            .build()

        return TokenResponse(
            accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue,
            expiresIn = properties.ttl.toSeconds(),
        )
    }
}
