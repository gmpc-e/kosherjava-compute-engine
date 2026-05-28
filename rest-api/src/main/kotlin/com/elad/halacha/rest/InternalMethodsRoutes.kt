package com.elad.halacha.rest

import com.elad.halacha.engine.internal.InternalMethodRegistry
import com.kosherjava.zmanim.util.GeoLocation
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

fun Route.internalMethodsRoutes() {
    route("/internal-methods") {
        get {
            call.respond(InternalMethodRegistry.all)
        }
        post("/compute") {
            val body = call.receive<ComputeBody>()

            val zoneId = runCatching { ZoneId.of(body.tz) }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid tz '${body.tz}'")
                )
            }

            val loc = GeoLocation(
                "request",
                body.lat,
                body.lon,
                body.elev ?: 0.0,
                TimeZone.getTimeZone(zoneId)
            )

            val dateInstant = body.date.atStartOfDay(zoneId).toInstant()

            val out = runCatching {
                InternalMethodRegistry.compute(
                    idString = body.id,
                    date = Date.from(dateInstant),
                    loc = loc,
                    params = body.params ?: emptyMap()
                )
            }.getOrElse { ex ->
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (ex.message ?: "Failed to compute internal method"))
                )
            }

            val resultTime = out.time
            val response = if (resultTime != null) {
                val instant = resultTime.toInstant()
                mapOf(
                    "id" to out.id.id,
                    "utc" to instant.atZone(ZoneId.of("UTC")).toString(),
                    "local_time" to instant.atZone(zoneId).toString()
                )
            } else {
                mapOf("id" to out.id.id, "utc" to null, "local_time" to null)
            }

            call.respond(response)
        }
    }
}

data class ComputeBody(
    val id: String,
    val date: LocalDate,
    val tz: String,
    val lat: Double,
    val lon: Double,
    val elev: Double? = null,
    val params: Map<String, Any?>? = null
)