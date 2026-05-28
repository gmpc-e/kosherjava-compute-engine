package com.elad.halacha.rest

import com.elad.halacha.engine.calendar.BannerLiteRequest
import com.elad.halacha.engine.calendar.BannerLiteService
import com.elad.halacha.engine.calendar.BannerLiteServiceImpl
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.elad.halacha.rest.BannerLiteRoutes")

/**
 * Banner-lite POC routes:
 *   GET  /api/banner-lite/ping
 *   POST /api/banner-lite/compute
 *
 * Assumes your app already has JSON ContentNegotiation (Jackson/kotlinx) enabled,
 * same as your existing /profiles endpoints.
 */
fun Route.bannerLiteRoutes(
    service: BannerLiteService = BannerLiteServiceImpl()
) {
    route("/api/banner-lite") {

        get("/ping") {
            log.info("GET /api/banner-lite/ping")
            call.respondText("pong", ContentType.Text.Plain)
        }

        post("/compute") {
            // Rely on your existing JSON plugin (Jackson/kotlinx) to parse directly.
            val req = runCatching { call.receive<BannerLiteRequest>() }
                .onFailure { e ->
                    log.error("Failed to parse BannerLiteRequest", e)
                }.getOrNull()

            if (req == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_request"))
                return@post
            }

            log.info(
                "Computing banner-lite date={} tz={} lat={} lon={} elev={} profileKey={}",
                req.dateIso, req.tz, req.lat, req.lon, req.elev, req.profileKey
            )

            val resp = service.compute(req)

            log.info(
                "Computed occasion={} method={} shaahSec={} minuteSec={} warnings={}",
                resp.occasion.code, resp.zmaniyot.method, resp.zmaniyot.shaahSeconds,
                resp.zmaniyot.minuteSeconds, resp.warnings
            )

            call.respond(HttpStatusCode.OK, resp)
        }
    }
}