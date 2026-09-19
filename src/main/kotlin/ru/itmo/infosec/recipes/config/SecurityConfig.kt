package ru.itmo.infosec.recipes.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import ru.itmo.infosec.recipes.security.JwtAuthFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            // API не использует cookie-сессии, поэтому CSRF-атака на него невозможна:
            // браузер не приложит к межсайтовому запросу заголовок Authorization сам.
            csrf { disable() }

            // Никаких серверных сессий: состояние клиента целиком в подписанном токене.
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }

            // Всё закрыто по умолчанию; публичны только регистрация и вход.
            authorizeHttpRequests {
                authorize("/auth/register", permitAll)
                authorize("/auth/login", permitAll)
                authorize(anyRequest, authenticated)
            }

            // Альтернативные способы входа отключены, чтобы остался ровно один
            // путь аутентификации и его было легко анализировать.
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }

            headers {
                // Браузер не должен угадывать тип содержимого: это классический путь
                // превращения JSON-ответа в исполняемый скрипт.
                contentTypeOptions { }
                frameOptions { deny = true }
                contentSecurityPolicy { policyDirectives = "default-src 'none'; frame-ancestors 'none'" }
                referrerPolicy { policy = ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER }
            }

            addFilterBefore<UsernamePasswordAuthenticationFilter>(jwtAuthFilter)

            // Неаутентифицированный запрос получает сухой 401 без WWW-Authenticate
            // и без редиректа на форму входа.
            exceptionHandling {
                authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)
            }
        }
        return http.build()
    }

    /**
     * bcrypt со cost-фактором 12.
     *
     * bcrypt намеренно медленный и содержит соль внутри хэша, поэтому в отличие от
     * SHA-256 он устойчив к перебору на GPU и к радужным таблицам.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    private companion object {
        const val BCRYPT_STRENGTH = 12
    }
}
