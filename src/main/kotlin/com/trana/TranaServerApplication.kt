package com.trana

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TranaServerApplication

fun main(args: Array<String>) {
    runApplication<TranaServerApplication>(*args)
}
