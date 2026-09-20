# Recipe API

REST API для хранения кулинарных рецептов. Лабораторная работа №1 по дисциплине
«Информационная безопасность», ИТМО.

Каждый пользователь работает только со своими рецептами. Доступ к данным — по JWT-токену,
выданному после входа.

Kotlin 2.3.21 · Spring Boot 4.1.1 · Spring Security 7.1.1 · Java 21 · H2 · Gradle 9.7.1

## Запуск

```bash
cp .env.example .env
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
./scripts/run.sh
```

Приложение стартует на `http://localhost:8080`. Без `JWT_SECRET` запуск прерывается намеренно.

## API

Публичны только `/auth/register` и `/auth/login`. Остальное требует заголовок
`Authorization: Bearer <token>`. Ошибки — в формате RFC 7807.

| Метод | Путь | Доступ | Назначение |
|---|---|---|---|
| `POST` | `/auth/register` | открыт | Регистрация: `{username, password}` |
| `POST` | `/auth/login` | открыт | Вход, выдача JWT |
| `GET` | `/api/data` | JWT | Список своих рецептов; `?q=`, `?page=`, `?size=` |
| `POST` | `/api/recipes` | JWT | Создать рецепт |
| `GET` | `/api/recipes/{id}` | JWT + владелец | Прочитать рецепт |
| `PUT` | `/api/recipes/{id}` | JWT + владелец | Обновить рецепт |
| `DELETE` | `/api/recipes/{id}` | JWT + владелец | Удалить рецепт |

```bash
curl -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct-horse-battery-staple"}'

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"correct-horse-battery-staple"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -X POST http://localhost:8080/api/recipes -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Борщ","description":"Классический борщ",
       "ingredients":["свёкла","капуста","говядина"],
       "instructions":"Сварить бульон, добавить овощи, тушить 40 минут.",
       "cookMinutes":120,"servings":6}'

curl -H "Authorization: Bearer $TOKEN" 'http://localhost:8080/api/data?q=борщ'
```

## Меры защиты

| Угроза | Решение | Где |
|---|---|---|
| SQL-инъекции (A03) | Derived-запросы Spring Data и JPQL с bind-параметрами; конкатенации SQL нет | [`RecipeRepository.kt`](src/main/kotlin/ru/itmo/infosec/recipes/repository/RecipeRepository.kt) |
| XSS (A03) | OWASP Java Encoder на выходе + валидация на входе + `nosniff`, `X-Frame-Options`, CSP | [`RecipeMapper.kt`](src/main/kotlin/ru/itmo/infosec/recipes/web/RecipeMapper.kt), [`SecurityConfig.kt`](src/main/kotlin/ru/itmo/infosec/recipes/config/SecurityConfig.kt) |
| Хранение паролей (A07) | bcrypt, cost 12; открытый пароль нигде не сохраняется | [`SecurityConfig.kt`](src/main/kotlin/ru/itmo/infosec/recipes/config/SecurityConfig.kt) |
| Аутентификация (A07) | JWT HS256 на 15 мин, ключ из окружения; свой фильтр поверх Nimbus `JwtDecoder`, алгоритм зафиксирован | [`JwtAuthFilter.kt`](src/main/kotlin/ru/itmo/infosec/recipes/security/JwtAuthFilter.kt), [`JwtConfig.kt`](src/main/kotlin/ru/itmo/infosec/recipes/config/JwtConfig.kt) |
| Перебор и разведка учёток | 5 попыток входа за 5 минут; ответ на неверный пароль неотличим от ответа на несуществующий логин, в том числе по времени | [`LoginRateLimiter.kt`](src/main/kotlin/ru/itmo/infosec/recipes/security/LoginRateLimiter.kt), [`AuthService.kt`](src/main/kotlin/ru/itmo/infosec/recipes/service/AuthService.kt) |
| Доступ к чужим данным (A01) | Выборка всегда ограничена владельцем; чужой рецепт отдаёт 404, а не 403 | [`RecipeRepository.kt`](src/main/kotlin/ru/itmo/infosec/recipes/repository/RecipeRepository.kt) |
| Утечка внутренних деталей | Наружу обезличенные сообщения, stacktrace только в лог | [`ApiExceptionHandler.kt`](src/main/kotlin/ru/itmo/infosec/recipes/web/ApiExceptionHandler.kt) |
| Уязвимые зависимости | `gradle.lockfile`, Tomcat поднят до 11.0.26, консоль H2 отключена, Dependabot | [`build.gradle.kts`](build.gradle.kts) |

## CI/CD

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) — на каждый push в `main` и каждый
pull request. Все actions закреплены по SHA коммита, права `GITHUB_TOKEN` минимальны.

| Job | Инструмент | Тип |
|---|---|---|
| `build` | Gradle, 21 тест | — |
| `sast-codeql` | CodeQL, `security-extended` | SAST |
| `sast-semgrep` | Semgrep, `p/kotlin` `p/java` `p/secrets` | SAST |
| `sca-trivy` | Trivy по fat-jar | SCA |
| `sca-osv` | OSV-Scanner по `gradle.lockfile` | SCA |

Последний прогон: [Actions → CI](https://github.com/arekalov/infosec-lab1-recipe-api/actions/workflows/ci.yml).
Находок нет: CodeQL — 0 из 120 правил, Semgrep — 0 из 117, Trivy — 0.

![Прогон CI](docs/img/ci-run.png)

![Список прогонов](docs/img/actions.png)

![Вкладка Security](docs/img/security.png)

## Тесты

```bash
./gradlew test       # 21 тест
./scripts/smoke.sh   # 31 сквозная проверка по живому API
```

Покрыты CRUD и валидация, отказ без токена и при подделанной или просроченной подписи,
формат хранения пароля, неразличимость ответов при входе, лимит попыток, SQL-инъекции,
экранирование XSS-нагрузки, доступ к чужим рецептам.

---

Рекалов Артём Олегович, группа P3409, 2026.
