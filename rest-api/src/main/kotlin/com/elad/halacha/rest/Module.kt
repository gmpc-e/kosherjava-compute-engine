package com.elad.halacha.rest

import com.elad.halacha.engine.EngineInfo
import com.elad.halacha.profiles.ProfileInfo
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.module() {
    install(ContentNegotiation) { jackson() }

    routing {
        get("/health") {
            call.respond(
                mapOf(
                    "status" to "ok",
                    "engine" to "${EngineInfo.NAME} v${EngineInfo.VERSION}",
                    "profiles" to "${ProfileInfo.NAME} v${ProfileInfo.VERSION}"
                )
            )
        }
    }
}
