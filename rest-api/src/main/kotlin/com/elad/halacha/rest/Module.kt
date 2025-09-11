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
import com.elad.halacha.rest.profiles.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue


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

        // --------------------------------------------------------------------
// PROFILES (read-only): list and fetch stored profile JSONs
// --------------------------------------------------------------------

// GET /profiles -> list available profiles (key, displayName, labels)
        get("/profiles") {
            log.debug("GET /profiles")
            val items = ProfileStore.list().map {
                mapOf(
                    "key" to it.key,
                    "displayName" to it.displayName,
                    "labels" to it.labels
                )
            }
            call.respond(HttpStatusCode.OK, items)
        }

// GET /profiles/{key} -> return full profile object
        get("/profiles/{key}") {
            val key = call.parameters["key"]
            if (key == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing profile key"))
                return@get
            }
            val profile = ProfileStore.get(key)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile '$key' not found"))
                return@get
            }
            call.respond(HttpStatusCode.OK, profile)
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

        // GET /profiles/{key}/compute?date=YYYY-MM-DD&lat=..&lon=..[&elev=0][&tz][&lang=he|en]
        get("/profiles/{key}/compute") {
            val key = call.parameters["key"]
            if (key == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing profile key"))
                return@get
            }
            val log = LoggerFactory.getLogger("Api")
            log.debug("GET /profiles/{}/compute uri={}", key, call.request.uri)

            val profile = ProfileStore.get(key)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile '$key' not found"))
                return@get
            }

            // Parse inputs with defaults (today if date missing; OS tz if tz missing)
            val inputs = call.parseInputsOr400(log) ?: return@get

            // Validate profile before compute
            val validation = ProfileValidator.validate(profile)
            if (!validation.valid) {
                call.respond(HttpStatusCode.BadRequest, validation)
                return@get
            }

            val results = profile.times.map { t ->
                when (t.target.kind) {
                    "EXTERNAL_NAME" -> {
                        val name = t.target.externalMethod!!
                        val req = ComputeRequest(
                            method = ComputeMethod.SUNSET, // ignored in by-name path
                            dateIso = inputs.date,
                            lat = inputs.lat,
                            lon = inputs.lon,
                            elevationMeters = inputs.elev,
                            tz = inputs.tz
                        )
                        val r = ZmanimComputer.computeByExternalName(name, req)
                        val owner = MethodRegistry.resolve(name)?.owner
                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = "EXTERNAL_NAME",
                                externalMethod = name,
                                owner = owner
                            ),
                            utc = r.utc,
                            local = r.local,
                            instant = r.instant?.toString()
                        )
                    }
                    "INTERNAL" -> {
                        // Not implemented yet – return unresolved stub
                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = "INTERNAL",
                                internalMethodId = t.target.internalMethodId,
                                owner = "INTERNAL",
                                status = "unresolved"
                            ),
                            utc = null,
                            local = null,
                            instant = null
                        )
                    }
                    else -> {
                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = t.target.kind,
                                status = "unresolved"
                            ),
                            utc = null,
                            local = null,
                            instant = null
                        )
                    }
                }
            }

            val resp = ProfileComputeResponse(
                profile = MinimalProfileInfo(profile.key, profile.displayName),
                input = ProfileComputeInput(
                    dateIso = inputs.date,
                    geo = GeoInput(inputs.lat, inputs.lon, inputs.elev, inputs.tz)
                ),
                results = results,
                warnings = validation.warnings
            )

            call.respond(HttpStatusCode.OK, resp)
        }
// Validate a profile (no persistence)
        post("/profiles/validate") {
            val body = call.receiveText()
            val mapper = jacksonObjectMapper()
            val profile: Profile = try {
                mapper.readValue(body)
            } catch (e: Exception) {
                log.warn("Profile parse error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, ValidationResponse(false, errors = listOf(
                    ValidationError("$", "invalid_json", e.message ?: "Invalid JSON")
                )))
                return@post
            }

            val res = ProfileValidator.validate(profile)
            val status = if (res.valid) HttpStatusCode.OK else HttpStatusCode.BadRequest
            call.respond(status, res)
        }

// Compute a profile (no persistence)
        post("/profiles/compute") {
            val mapper = jacksonObjectMapper()
            val body = call.receiveText()
            val profile: Profile = try {
                mapper.readValue(body)
            } catch (e: Exception) {
                log.warn("Profile parse error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid profile JSON"))
                return@post
            }

            // Validate first
            val validation = ProfileValidator.validate(profile)
            if (!validation.valid) {
                call.respond(HttpStatusCode.BadRequest, validation)
                return@post
            }

            // inputs (reuse your parser defaults: date=today if missing; tz=OS if missing)
            val inputs = call.parseInputsOr400(log) ?: return@post

            // compute every time in order
            val results = profile.times.map { t ->
                when (t.target.kind) {
                    "EXTERNAL_NAME" -> {
                        val name = t.target.externalMethod!!
                        val req = com.elad.halacha.engine.model.ComputeRequest(
                            method = com.elad.halacha.engine.model.ComputeMethod.SUNSET, // ignored in by-name compute
                            dateIso = inputs.date,
                            lat = inputs.lat,
                            lon = inputs.lon,
                            elevationMeters = inputs.elev,
                            tz = inputs.tz
                        )
                        val result = com.elad.halacha.engine.compute.ZmanimComputer.computeByExternalName(name, req)
                        val owner = com.elad.halacha.engine.compute.MethodRegistry.resolve(name)?.owner

                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = "EXTERNAL_NAME",
                                externalMethod = name,
                                owner = owner
                            ),
                            utc = result.utc,
                            local = result.local,
                            instant = result.instant?.toString()  // <-- stringify Instant
                        )
                    }
                    "INTERNAL" -> {
                        // Not implemented yet – return unresolved
                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = "INTERNAL",
                                internalMethodId = t.target.internalMethodId,
                                owner = "INTERNAL",
                                status = "unresolved"
                            ),
                            utc = null,
                            local = null,
                            instant = null
                        )
                    }
                    else -> {
                        // Should not happen after validation
                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = t.target.kind,
                                status = "unresolved"
                            ),
                            utc = null,
                            local = null,
                            instant = null
                        )
                    }
                }
            }

            val resp = ProfileComputeResponse(
                profile = MinimalProfileInfo(profile.key, profile.displayName),
                input = ProfileComputeInput(
                    dateIso = inputs.date,
                    geo = GeoInput(inputs.lat, inputs.lon, inputs.elev, inputs.tz)
                ),
                results = results,
                warnings = validation.warnings
            )
            call.respond(HttpStatusCode.OK, resp)
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