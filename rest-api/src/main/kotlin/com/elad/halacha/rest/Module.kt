package com.elad.halacha.rest
import java.time.LocalDate
import java.time.ZoneId
import com.elad.halacha.engine.EngineInfo
import com.elad.halacha.engine.compute.MethodRegistry
import com.elad.halacha.engine.compute.ZmanimComputer
import com.elad.halacha.engine.model.ComputeMethod
import com.elad.halacha.engine.model.ComputeRequest
import com.elad.halacha.profiles.ProfileInfo
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*   // <- for call.request.uri
import org.slf4j.LoggerFactory

fun Application.module() {
    // ---- JSON config (support java.time.* and ISO-8601 output) ----
    install(ContentNegotiation) {
        jackson {
            registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    val log = LoggerFactory.getLogger("Api")

    routing {
        // Health
        get("/health") {
            log.debug("GET /health")
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "ok",
                    "engine" to "${EngineInfo.NAME} v${EngineInfo.VERSION}",
                    "profiles" to "${ProfileInfo.NAME} v${ProfileInfo.VERSION}"
                )
            )
        }

        // List available 3rd-party methods (zero-arg, Date-returning)
        get("/methods") {
            log.debug("GET /methods")
            val methods = MethodRegistry.available.map { m ->
                mapOf(
                    "name" to m.name,
                    "owner" to m.owner,
                    "description" to MethodDocs.describe(m.name) // <- optional, may be null
                )
            }
            log.info("Methods listed: {}", methods.size)
            call.respond(HttpStatusCode.OK, methods)
        }

        // Compute by enum name
        // GET /compute/{method}?date=...&lat=...&lon=...&elev=...&tz=...
        get("/compute/{method}") {
            log.debug("GET {} uri={}", "/compute/${call.parameters["method"]}", call.request.uri)

            val methodParam = call.parameters["method"]
            if (methodParam == null) {
                call.badRequest(log, "Missing path param 'method'")
                return@get
            }

            val method = try {
                ComputeMethod.valueOf(methodParam.uppercase())
            } catch (_: IllegalArgumentException) {
                call.badRequest(log, "Unsupported enum method '$methodParam'")
                return@get
            }

            val inputs = call.parseInputsOr400(log) ?: return@get
            log.info(
                "Compute(enum) method={} date={} lat={} lon={} elev={} tz={}",
                method, inputs.date, inputs.lat, inputs.lon, inputs.elev, inputs.tz
            )

            // Map enum -> actual KosherJava method + owner (parent class)
            val (externalMethod, owner) = when (method) {
                ComputeMethod.SUNRISE -> "getSunrise" to "ZmanimCalendar"
                ComputeMethod.SUNSET -> "getSunset" to "ZmanimCalendar"
                ComputeMethod.SEA_LEVEL_SUNSET -> "getSeaLevelSunset" to "AstronomicalCalendar"
            }

            val req = ComputeRequest(
                method = method,
                dateIso = inputs.date,
                lat = inputs.lat,
                lon = inputs.lon,
                elevationMeters = inputs.elev,
                tz = inputs.tz
            )

            val result = ZmanimComputer.compute(req)
            log.debug("Compute(enum) result: utc={}, local={}", result.utc, result.local)

            // Respond with resolution info (actual external method + owner), plus the computed times
            val response = mapOf(
                "resolution" to mapOf(
                    "kind" to "ENUM",
                    "alias" to method.name,
                    "externalMethod" to externalMethod,
                    "owner" to owner
                ),
                "input" to mapOf(
                    "dateIso" to inputs.date,
                    "lat" to inputs.lat,
                    "lon" to inputs.lon,
                    "elevationMeters" to inputs.elev,
                    "tz" to inputs.tz
                ),
                "utc" to result.utc,
                "local" to result.local,
                "instant" to result.instant
            )

            call.respond(HttpStatusCode.OK, response)
        }

        // Compute by exact 3rd-party method name
        // GET /compute/by-name?method=getSeaLevelSunset&date=...&lat=...&lon=...&elev=...&tz=...
        get("/compute/by-name") {
            log.debug("GET /compute/by-name uri={}", call.request.uri)

            val name = call.request.queryParameters["method"]
            if (name == null) {
                call.badRequest(log, "Missing query param 'method'")
                return@get
            }

            val inputs = call.parseInputsOr400(log) ?: return@get

            log.info(
                "Compute(by-name) method={} date={} lat={} lon={} elev={} tz={}",
                name, inputs.date, inputs.lat, inputs.lon, inputs.elev, inputs.tz
            )

            val req = ComputeRequest(
                method = ComputeMethod.SUNSET, // internal field, ignored in by-name response
                dateIso = inputs.date,
                lat = inputs.lat,
                lon = inputs.lon,
                elevationMeters = inputs.elev,
                tz = inputs.tz
            )

            val result = ZmanimComputer.computeByExternalName(name, req)
            if (result.local == null) {
                log.warn("Compute(by-name) not computable or unsupported: {}", name)
                call.badRequest(log, "Unsupported or non-computable method '$name'")
                return@get
            }

            val owner = MethodRegistry.resolve(name)?.owner

            // Minimal response: external method + owner + computed times (no internal enum)
            val response = mapOf(
                "resolution" to mapOf(
                    "kind" to "EXTERNAL_NAME",
                    "externalMethod" to name,
                    "owner" to owner
                ),
                "input" to mapOf(
                    "dateIso" to inputs.date,
                    "lat" to inputs.lat,
                    "lon" to inputs.lon,
                    "elevationMeters" to inputs.elev,
                    "tz" to inputs.tz
                ),
                "utc" to result.utc,
                "local" to result.local,
                "instant" to result.instant
            )

            log.debug("Compute(by-name) result: utc={}, local={}", result.utc, result.local)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

private data class Inputs(
    val date: String,
    val lat: Double,
    val lon: Double,
    val elev: Double,
    val tz: String
)

/** Parse shared inputs, log problems, and respond 400 when invalid. */
private suspend fun ApplicationCall.parseInputsOr400(log: org.slf4j.Logger): Inputs? {
    val tz = request.queryParameters["tz"] ?: ZoneId.systemDefault().id

    val date = request.queryParameters["date"] ?: run {
        val today = LocalDate.now(ZoneId.of(tz)).toString()
        log.debug("No 'date' provided, defaulting to today in tz {}: {}", tz, today)
        today
    }

    val lat = request.queryParameters["lat"]?.toDoubleOrNull()
    if (lat == null) {
        badRequest(log, "Missing/invalid 'lat'")
        return null
    }

    val lon = request.queryParameters["lon"]?.toDoubleOrNull()
    if (lon == null) {
        badRequest(log, "Missing/invalid 'lon'")
        return null
    }

    val elev = request.queryParameters["elev"]?.toDoubleOrNull() ?: 0.0

    log.debug("Parsed inputs: date={} lat={} lon={} elev={} tz={}", date, lat, lon, elev, tz)
    return Inputs(date, lat, lon, elev, tz)
}

/** Respond 400 and log a clear message. Returns Unit so callers can `return@get` explicitly. */
private suspend fun ApplicationCall.badRequest(
    log: org.slf4j.Logger,
    msg: String
) {
    log.warn("400 Bad Request: {}", msg)
    respond(HttpStatusCode.BadRequest, mapOf("error" to msg))
}