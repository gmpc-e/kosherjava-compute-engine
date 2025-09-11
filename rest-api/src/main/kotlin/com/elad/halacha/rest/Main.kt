package com.elad.halacha.rest

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory

fun main() {
    val log = LoggerFactory.getLogger("Main")
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    log.info("Starting Ktor on port {}", port)
    embeddedServer(Netty, port = port) { module() }.start(wait = true)
}
