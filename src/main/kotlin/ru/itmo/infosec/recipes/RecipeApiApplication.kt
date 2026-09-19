package ru.itmo.infosec.recipes

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RecipeApiApplication

fun main(args: Array<String>) {
	runApplication<RecipeApiApplication>(*args)
}
