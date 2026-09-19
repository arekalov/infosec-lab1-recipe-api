plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.4.20"
}

group = "ru.itmo.infosec"
version = "0.0.1-SNAPSHOT"
description = "Secure REST API lab 1"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

// Spring Boot 4.1.1 тянет Tomcat 11.0.24, у которого три CRITICAL-уязвимости
// (GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5), закрытые в 11.0.25.
// Без этого пина SCA-сканер в CI найдёт их в fat-jar.
extra["tomcat.version"] = "11.0.26"

repositories {
	mavenCentral()
}

dependencies {
	// spring-boot-h2console сознательно не подключён: веб-консоль H2 — источник
	// исторических RCE (CVE-2021-42392, CVE-2022-23221) и в защищённом API ей не место.
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	// Ради NimbusJwtEncoder/NimbusJwtDecoder: версия nimbus-jose-jwt управляется Spring BOM,
	// в отличие от сторонних JWT-библиотек, которые тянут собственный Jackson.
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	// OWASP Java Encoder — контекстное экранирование пользовательского текста в ответах.
	implementation("org.owasp.encoder:encoder:1.4.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("com.h2database:h2")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Блокировка версий runtime-зависимостей.
//
// Зафиксированный gradle.lockfile даёт две вещи: сборка перестаёт зависеть от того,
// что именно окажется в репозитории на момент запуска, и у SCA-сканеров появляется
// достоверный перечень версий — без него они видят только объявленные координаты.
//
// Обновление после изменения зависимостей:
//   ./gradlew dependencies --configuration runtimeClasspath --write-locks
configurations.named("runtimeClasspath") {
	resolutionStrategy.activateDependencyLocking()
}

