package com.elad.halacha.engine.internal

import com.kosherjava.zmanim.AstronomicalCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.TimeZone
import kotlin.math.roundToLong

object InternalMethodComputer {

    private val log = LoggerFactory.getLogger(InternalMethodComputer::class.java)
    private val isoFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    data class Inputs(
        val date: Date,
        val location: GeoLocation,
        /** Used only by degree-based twilights (tzeis variants). Default 18°. */
        val astroDegrees: Double = 18.0
    )

    data class Output(val id: InternalMethodId, val time: Date?)

    // ----- Instant helpers (avoid Date.toInstant() for maximum compatibility) -----
    private fun toInstantSafe(d: Date): Instant = Instant.ofEpochMilli(d.time)
    private fun toInstantSafeOrNull(d: Date?): Instant? = d?.let { Instant.ofEpochMilli(it.time) }

    private fun Date?.fmt(tz: TimeZone): String =
        toInstantSafeOrNull(this)?.atZone(ZoneId.of(tz.id))?.let { isoFmt.format(it) } ?: "null"

    private fun millisToHms(ms: Long): String {
        val totalSec = (ms / 1000.0).roundToLong()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    // ---------- Baselines ----------

    /** Sea-level sunrise/sunset (netz/shkiah, no elevation). */
    private fun seaLevelRiseSet(inputs: Inputs): Pair<Date, Date>? {
        val ac = AstronomicalCalendar(inputs.location).apply { calendar.time = inputs.date }
        val tz = inputs.location.timeZone

        val sunrise = ac.seaLevelSunrise
        val sunset  = ac.seaLevelSunset
        if (sunrise == null || sunset == null || !sunset.after(sunrise)) return null

        log.debug(
            "# Sea-level Sunrise: {} (local {}), method: AstronomicalCalendar.getSeaLevelSunrise()",
            toInstantSafe(sunrise).atZone(ZoneId.of("UTC")),
            sunrise.fmt(tz)
        )
        log.debug(
            "# Sea-level Sunset:  {} (local {}), method: AstronomicalCalendar.getSeaLevelSunset()",
            toInstantSafe(sunset).atZone(ZoneId.of("UTC")),
            sunset.fmt(tz)
        )
        return sunrise to sunset
    }

    /** ASTRO(18° by default) sunrise/sunset for this date/location. */
    private fun astroRiseSet18(inputs: Inputs): Pair<Date, Date>? {
        val tz = inputs.location.timeZone
        val ac = AstronomicalCalendar(inputs.location).apply { calendar.time = inputs.date }
        val zenith = AstronomicalCalendar.GEOMETRIC_ZENITH + inputs.astroDegrees

        val rise = ac.getSunriseOffsetByDegrees(zenith)
        val set  = ac.getSunsetOffsetByDegrees(zenith)
        if (rise == null || set == null || !set.after(rise)) return null

        log.debug("# Baseline: ASTRO_{}° (AstronomicalCalendar.getSunrise/SetOffsetByDegrees)", inputs.astroDegrees)
        log.debug("# Astro Sunrise: {} (local {})", toInstantSafe(rise).atZone(ZoneId.of("UTC")), rise.fmt(tz))
        log.debug("# Astro Sunset : {} (local {})", toInstantSafe(set).atZone(ZoneId.of("UTC")), set.fmt(tz))
        return rise to set
    }

    // ---------- Zmanit helpers ----------

    /** Shaah zmanit from *sea-level* day (netz→shkiah). */
    fun shaahZmanitSeaLevel(inputs: Inputs): Long {
        val (sunrise, sunset) = seaLevelRiseSet(inputs) ?: error(
            "Sea-level sunrise/sunset unavailable for ${inputs.date} at ${inputs.location}."
        )
        val dayMillis = sunset.time - sunrise.time
        val shaah = dayMillis / 12L
        log.debug(
            "# [Sea-level day] dayLength={} ({}), shaahZmanit={} ({} min)",
            dayMillis, millisToHms(dayMillis), shaah, "%.3f".format(shaah / 60000.0)
        )
        return shaah
    }

    /** Shaah zmanit for **expanded day** (±72 zmaniyot around astro 18° day). base/9.6. */
    private fun shaahZmanitAstroExpanded72(inputs: Inputs): Long {
        val (rise, set) = astroRiseSet18(inputs) ?: error(
            "Astronomical sunrise/sunset unavailable for ${inputs.date} at ${inputs.location}."
        )
        val base = set.time - rise.time
        val shaah = (base / 9.6).roundToLong() // base = 9.6*shaah → shaah = base/9.6
        val tz = inputs.location.timeZone

        val start = Date(rise.time - (1.2 * shaah).roundToLong())
        val end   = Date(set.time  + (1.2 * shaah).roundToLong())
        log.debug(
            "# [Astro expanded day] base={} ({}), shaah={} ({} min)",
            base, millisToHms(base), shaah, "%.3f".format(shaah / 60000.0)
        )
        log.debug(
            "# [Astro expanded day] start={} (UTC {}), end={} (UTC {}), len={} ({})",
            start.fmt(tz), toInstantSafe(start).atZone(ZoneId.of("UTC")),
            end.fmt(tz), toInstantSafe(end).atZone(ZoneId.of("UTC")),
            (end.time - start.time), millisToHms(end.time - start.time)
        )
        return shaah
    }

    /** Shaah zmanit for the *astronomical* day (astronomical sunrise → astronomical sunset). */
    private fun shaahZmanitAstronomicalDay(inputs: Inputs): Long? {
        val (riseA, setA) = astroRiseSet18(inputs) ?: return null
        if (!setA.after(riseA)) return null

        val base = setA.time - riseA.time
        val shaah = base / 12L
        val tz = inputs.location.timeZone

        log.debug(
            "# [Astro day] base={} ({}), shaahZmanit={} ({} min)",
            base, millisToHms(base), shaah, "%.3f".format(shaah / 60000.0)
        )
        log.debug(
            "# Astro Day: sunrise={} (UTC {}), sunset={} (UTC {})",
            riseA.fmt(tz), toInstantSafe(riseA).atZone(ZoneId.of("UTC")),
            setA.fmt(tz), toInstantSafe(setA).atZone(ZoneId.of("UTC"))
        )
        return shaah
    }

    // ---------- Offsets ----------

    /** Offset by N *zmaniyot minutes* = minutes * (shaah/60). Negative = before. */
    fun offsetByProportionalMinutes(base: Date, shaahZmanitMs: Long, minutes: Double): Date {
        val perMinMs = shaahZmanitMs / 60.0
        val deltaMs = minutes * perMinMs
        return Date(base.time + deltaMs.roundToLong())
    }

    /** Offset by H *zmaniyot hours* = hours * shaah. Negative = before. */
    fun offsetByProportionalHours(base: Date, shaahZmanitMs: Long, hours: Double): Date {
        val deltaMs = (hours * shaahZmanitMs.toDouble()).roundToLong()
        return Date(base.time + deltaMs)
    }

    // ---------- Public internal methods ----------

    /** ALOS by **72 zmaniyot minutes before** *sea-level* sunrise. */
    fun alot72ZmaniyotSea(inputs: Inputs): Date? {
        val tz = inputs.location.timeZone
        val ac = AstronomicalCalendar(inputs.location).apply { calendar.time = inputs.date }
        val sunrise = ac.seaLevelSunrise ?: return null
        val shaah = shaahZmanitSeaLevel(inputs)

        log.debug(
            "# Computing internal method: ALOS by 72 proportional minutes BEFORE Sea-level Sunrise ({})",
            sunrise.fmt(tz)
        )
        log.debug("# Formula: alos = seaLevelSunrise - (72 zmanit minutes)")

        val result = offsetByProportionalMinutes(sunrise, shaah, -72.0)
        log.debug(
            "# Result: ALOS_72_ZMANIYOT = {} (UTC {})",
            result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
        )
        return result
    }

    /** MISHEYAKIR by **zmanit hours** relative to sea-level sunrise. Default −1.1h. */
    fun misheyakirShaotZmaniyotSea(inputs: Inputs, hoursFromSeaLevelSunrise: Double = -1.1): Date? {
        val tz = inputs.location.timeZone
        val ac = AstronomicalCalendar(inputs.location).apply { calendar.time = inputs.date }
        val sunrise = ac.seaLevelSunrise ?: return null
        val shaah = shaahZmanitSeaLevel(inputs)

        log.debug(
            "# Computing MISHEYAKIR by {} zmanit hours from Sea-level Sunrise ({})",
            hoursFromSeaLevelSunrise, sunrise.fmt(tz)
        )
        log.debug("# Formula: misheyakir = seaLevelSunrise + ({} * shaahZmanit)", hoursFromSeaLevelSunrise)

        val result = offsetByProportionalHours(sunrise, shaah, hoursFromSeaLevelSunrise)
        log.debug(
            "# Result: MISHEYAKIR = {} (UTC {})",
            result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
        )
        return result
    }

    /** TZAIS at a given degree depression (e.g., 4.9°). */
    fun tzaisByDegrees(inputs: Inputs, degrees: Double): Date? {
        val tz = inputs.location.timeZone
        val ac = AstronomicalCalendar(inputs.location).apply { calendar.time = inputs.date }
        val zenith = AstronomicalCalendar.GEOMETRIC_ZENITH + degrees
        log.debug(
            "# Computing TZAIS at {}°; method: AstronomicalCalendar.getSunsetOffsetByDegrees({})",
            degrees, zenith
        )
        val result = ac.getSunsetOffsetByDegrees(zenith)
        log.debug(
            "# Result: TZAIS({}°) = {} (UTC {})",
            degrees, result.fmt(tz), toInstantSafeOrNull(result)?.atZone(ZoneId.of("UTC"))
        )
        return result
    }

    // ---- Sof zmanim ----

    /** SZ"S (Gra/Tanya): 3 zmaniyot hours from *sea-level* sunrise → *sea-level* sunset (base/12). */
    fun sofZmanShmaGraSea(inputs: Inputs): Date? {
        val tz = inputs.location.timeZone
        val (rise, set) = seaLevelRiseSet(inputs) ?: return null
        val baseLen = set.time - rise.time
        val shaah = baseLen / 12L
        log.debug(
            "# SZ\"S Gra (SEA-LEVEL): shaah=base/12 → base={} ({}), shaah={} ({} min)",
            baseLen, millisToHms(baseLen), shaah, "%.3f".format(shaah / 60000.0)
        )
        val result = Date(rise.time + 3L * shaah)
        log.debug(
            "# SZ\"S Gra = sunrise + 3·shaah = {} (UTC {})",
            result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
        )
        return result
    }

    /** SZ\"T (MGA): 4 zmaniyot hours using **expanded day** around *sea-level* baseline: shaah=base/9.6. */
    fun sofZmanTefillaMgaSeaExpanded(inputs: Inputs): Date? {
        val tz = inputs.location.timeZone
        val (rise, set) = seaLevelRiseSet(inputs) ?: return null
        val baseLen = set.time - rise.time
        val shaah = (baseLen / 9.6).roundToLong()
        val start = Date(rise.time - (1.2 * shaah).roundToLong())
        log.debug(
            "# SZ\"T MGA (SEA-LEVEL expanded): shaah=base/9.6 → base={} ({}), shaah={} ({} min)",
            baseLen, millisToHms(baseLen), shaah, "%.3f".format(shaah / 60000.0)
        )
        log.debug(
            "# Expanded start = seaLevelSunrise - 1.2·shaah = {} (UTC {})",
            start.fmt(tz), toInstantSafe(start).atZone(ZoneId.of("UTC"))
        )
        val result = Date(start.time + 4L * shaah)
        log.debug(
            "# SZ\"T MGA = start + 4·shaah = {} (UTC {})",
            result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
        )
        return result
    }

    /** SZ\"T (Gra/Tanya): 4 zmaniyot hours from *sea-level* sunrise → *sea-level* sunset (base/12). */
    fun sofZmanTefillaGraSea(inputs: Inputs): Date? {
        val tz = inputs.location.timeZone
        val (rise, set) = seaLevelRiseSet(inputs) ?: return null
        val baseLen = set.time - rise.time
        val shaah = baseLen / 12L
        log.debug(
            "# SZ\"T Gra (SEA-LEVEL): shaah=base/12 → base={} ({}), shaah={} ({} min)",
            baseLen, millisToHms(baseLen), shaah, "%.3f".format(shaah / 60000.0)
        )
        val result = Date(rise.time + 4L * shaah)
        log.debug(
            "# SZ\"T Gra = sunrise + 4·shaah = {} (UTC {})",
            result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
        )
        return result
    }

    /** SZ\"S (MGA) off **ASTRO 18° expanded** (kept for completeness). */
    fun sofZmanShmaMgaAstroExpanded(inputs: Inputs): Date? {
        val tz = inputs.location.timeZone
        val (rise, set) = astroRiseSet18(inputs) ?: return null
        val baseLen = set.time - rise.time
        val shaah = (baseLen / 9.6).roundToLong()
        val start = Date(rise.time - (1.2 * shaah).roundToLong())
        log.debug(
            "# SZ\"S MGA (ASTRO expanded): shaah=base/9.6 → base={} ({}), shaah={} ({} min)",
            baseLen, millisToHms(baseLen), shaah, "%.3f".format(shaah / 60000.0)
        )
        val result = Date(start.time + 3L * shaah)
        log.debug(
            "# SZ\"S MGA = start + 3·shaah = {} (UTC {})",
            result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
        )
        return result
    }

    /** Tzais = astronomical sunset + 13.5 zmanit minutes (day = astro sunrise→astro sunset). */
    /** Tzais = sea-level sunset + 13.5 zmanit minutes (shaah from sea-level day). */
    fun tzais13p5ZmaniyotAstro(inputs: Inputs): Date? {
                val tz = inputs.location.timeZone
                val (rise, set) = seaLevelRiseSet(inputs) ?: return null
                require(set.after(rise)) { "Sea-level sunset must be after sunrise" }

                val baseLen = set.time - rise.time
                val shaah = baseLen / 12L
                val perZmanitMinuteMs = shaah / 60.0
                val offsetMs = (13.5 * perZmanitMinuteMs).roundToLong()
                val result = Date(set.time + offsetMs)

                log.debug("# TZAIS(13.5 zmanit) baseline: SEA-LEVEL sunrise/sunset")
                log.debug("# Sea-level sunrise: {} (UTC {})", rise.fmt(tz), toInstantSafe(rise).atZone(ZoneId.of("UTC")))
                log.debug("# Sea-level sunset : {} (UTC {})", set.fmt(tz), toInstantSafe(set).atZone(ZoneId.of("UTC")))
                log.debug(
                    "# Shaah from sea-level day: baseLen={} ({}), shaah={}ms ({} min), perZmanitMinute={} ms",
                    baseLen, millisToHms(baseLen), shaah, "%.3f".format(shaah / 60000.0), "%.3f".format(perZmanitMinuteMs)
                )
                log.debug("# Formula: tzais = seaLevelSunset + 13.5 · (shaah/60)")
                log.debug("# Result: TZAIS_13P5_ZMANIYOT = {} (UTC {})",
                    result.fmt(tz), toInstantSafe(result).atZone(ZoneId.of("UTC"))
                )

                return result
            }
    }

