package ru.itmo.infosec.recipes.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder

/**
 * Подпись и проверка токенов — симметричный HS256 на общем секрете.
 *
 * Декодер настроен явным алгоритмом: без этого токен с заголовком `alg: none`
 * или с другим алгоритмом мог бы быть принят (классическая атака на JWT).
 */
@Configuration
class JwtConfig {

    @Bean
    fun jwtEncoder(properties: JwtProperties): JwtEncoder =
        NimbusJwtEncoder.withSecretKey(properties.secretKey())
            .algorithm(MacAlgorithm.HS256)
            .build()

    @Bean
    fun jwtDecoder(properties: JwtProperties): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(properties.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
}
