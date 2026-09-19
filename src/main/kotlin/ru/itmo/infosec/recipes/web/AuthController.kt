package ru.itmo.infosec.recipes.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.itmo.infosec.recipes.service.AuthService
import ru.itmo.infosec.recipes.web.dto.LoginRequest
import ru.itmo.infosec.recipes.web.dto.RegisterRequest
import ru.itmo.infosec.recipes.web.dto.TokenResponse
import ru.itmo.infosec.recipes.web.dto.UserResponse

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest): UserResponse = authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse = authService.login(request)
}
