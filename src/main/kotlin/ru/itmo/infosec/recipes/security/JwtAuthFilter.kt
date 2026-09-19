package ru.itmo.infosec.recipes.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Middleware, проверяющий JWT на каждом запросе.
 *
 * Работает поверх [JwtDecoder], который проверяет HMAC-подпись, алгоритм и срок
 * действия токена. Никакой ручной разбор Base64 здесь не делается — это ровно тот
 * случай, когда самописная криптография была бы уязвимостью, а не защитой.
 *
 * Если токен есть, но невалиден, запрос немедленно отклоняется с 401: молча
 * пропускать его дальше как анонимный означало бы скрывать проблему от клиента.
 */
@Component
class JwtAuthFilter(private val jwtDecoder: JwtDecoder) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = bearerToken(request)
        if (token == null || SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val jwt = jwtDecoder.decode(token)
            // Токен без subject не идентифицирует пользователя — доверять ему нечему.
            val subject = jwt.subject ?: throw BadJwtException("Token has no subject")
            val authorities = (jwt.getClaimAsStringList(CLAIM_ROLES) ?: emptyList())
                .map { SimpleGrantedAuthority("ROLE_$it") }
            val authentication = UsernamePasswordAuthenticationToken(subject, null, authorities)
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authentication
        } catch (ex: JwtException) {
            // В лог — причина, клиенту — только статус: подробности помогли бы
            // подбирать токен (истёк? не та подпись? не тот алгоритм?).
            log.debug("Rejected JWT: {}", ex.message)
            SecurityContextHolder.clearContext()
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun bearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        return header.substring(BEARER_PREFIX.length).trim().ifEmpty { null }
    }

    companion object {
        const val CLAIM_ROLES = "roles"
        private const val BEARER_PREFIX = "Bearer "
    }
}
