package com.elad.halacha.engine.compute

import com.elad.halacha.engine.model.ComputeMethod
import com.elad.halacha.engine.model.ComputeRequest
import com.elad.halacha.engine.model.ComputeResult
import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.ZmanimCalendar
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

object ZmanimComputer {

    private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TS_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * Compute by enum method (limited set we expose as first-class enums).
     */
    fun compute(req: ComputeRequest): ComputeResult {
        val zone = ZoneId.of(req.tz)
        val date = LocalDate.parse(req.dateIso, DATE_FMT)
        val tz = TimeZone.getTimeZone(zone)
        val geo = GeoLocation(
            /* locationName = */ "user",
            /* latitude =     */ req.lat,
            /* longitude =    */ req.lon,
            /* elevation =    */ (req.elevationMeters ?: 0.0),
            /* timeZone =     */ tz
        )

        // KosherJava calendars
        val ac = AstronomicalCalendar(geo)
        val zc = ZmanimCalendar(geo)

        // Apply date to both (they use java.util.Calendar under the hood)
        val cal = GregorianCalendar.from(date.atStartOfDay(zone))
        ac.calendar = cal
        zc.calendar = cal

        val dateObj: Date? = when (req.method) {
            ComputeMethod.SUNRISE -> zc.sunrise
            ComputeMethod.SUNSET -> zc.sunset
            ComputeMethod.SEA_LEVEL_SUNSET -> ac.seaLevelSunset
        }

        val instant = dateObj?.toInstant()
        val utcIso = instant?.atZone(ZoneOffset.UTC)?.format(TS_FMT)
        val localIso = instant?.atZone(zone)?.format(TS_FMT)

        return ComputeResult(
            method = req.method,
            input = req,
            utc = utcIso,
            local = localIso,
            instant = instant
        )
    }

    /**
     * Compute by exact external 3rd-party method name (e.g., "getSunrise", "getSunset", "getSeaLevelSunset").
     * Supports only zero-arg methods that return java.util.Date, across:
     *  - ComplexZmanimCalendar
     *  - ZmanimCalendar
     *  - AstronomicalCalendar
     *
     * If method is not found or not computable, utc/local will be null.
     */
    fun computeByExternalName(
        methodName: String,
        req: ComputeRequest
    ): ComputeResult {
        val zone = ZoneId.of(req.tz)
        val date = LocalDate.parse(req.dateIso, DATE_FMT)
        val tz = TimeZone.getTimeZone(zone)
        val geo = GeoLocation("user", req.lat, req.lon, (req.elevationMeters ?: 0.0), tz)

        val ac = AstronomicalCalendar(geo)
        val zc = ZmanimCalendar(geo)
        val czc = ComplexZmanimCalendar(geo)

        val cal = GregorianCalendar.from(date.atStartOfDay(zone))
        ac.calendar = cal
        zc.calendar = cal
        czc.calendar = cal

        // Resolve which calendar exposes the method
        val ext = MethodRegistry.resolve(methodName)

        val target: Any? = when (ext?.owner) {
            "ComplexZmanimCalendar" -> czc
            "ZmanimCalendar" -> zc
            "AstronomicalCalendar" -> ac
            else -> null
        }

        val dateObj: Date? = target
            ?.let { t ->
                t::class.java.methods.firstOrNull {
                    it.name.equals(methodName, ignoreCase = true) &&
                            it.parameterCount == 0 &&
                            Date::class.java.isAssignableFrom(it.returnType)
                }?.invoke(t) as? Date
            }

        val instant = dateObj?.toInstant()
        val utcIso = instant?.atZone(ZoneOffset.UTC)?.format(TS_FMT)
        val localIso = instant?.atZone(zone)?.format(TS_FMT)

        // Note: for by-name, req.method is not semantically meaningful; we return it unchanged.
        return ComputeResult(
            method = req.method,
            input = req,
            utc = utcIso,
            local = localIso,
            instant = instant
        )
    }
}