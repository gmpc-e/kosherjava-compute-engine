package com.elad.halacha.engine.calendar

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

/**
 * Computes Shabbat-specific times that should be appended to the regular
 * profile results list:
 *  - Friday  → candle lighting (default 18 min before sunset)
 *  - Saturday → Shabbat ends (Tzais / nightfall)
 *
 * Returns null when the requested date is neither Friday nor Saturday.
 */
object ShabbatTimesComputer {

    private val log = LoggerFactory.getLogger(ShabbatTimesComputer::class.java)
    private val TS_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    data class ShabbatTimeItem(
        val id: String,
        val labelHe: String,
        val labelEn: String,
        val utc: String?,
        val local: String?,
        val instant: String?
    )

    /**
     * @param dateIso  "YYYY-MM-DD"
     * @param geo      GeoLocation (includes TimeZone)
     * @param candleOffsetMinutes  minutes before sunset for candle lighting (default 18)
     * @return a list of 0-or-1 ShabbatTimeItem entries, or empty if not Friday/Saturday
     */
    fun compute(
        dateIso: String,
        geo: GeoLocation,
        candleOffsetMinutes: Double = 18.0
    ): List<ShabbatTimeItem> {
        val zoneId = ZoneId.of(geo.timeZone.id)
        val localDate = LocalDate.parse(dateIso)
        val cal = GregorianCalendar.from(localDate.atStartOfDay(zoneId))
        val jc = JewishCalendar(cal)

        val dayOfWeek = jc.dayOfWeek
        log.debug("ShabbatTimesComputer date={} dayOfWeek={} (FRI=6, SAT=7)", dateIso, dayOfWeek)

        if (dayOfWeek != Calendar.FRIDAY && dayOfWeek != Calendar.SATURDAY) {
            return emptyList()
        }

        val czc = ComplexZmanimCalendar(geo).apply {
            calendar = GregorianCalendar.from(localDate.atTime(12, 0).atZone(zoneId))
        }

        return when (dayOfWeek) {
            Calendar.FRIDAY -> {
                val prevOffset = czc.candleLightingOffset
                czc.candleLightingOffset = candleOffsetMinutes
                val candleDate: Date? = czc.candleLighting
                czc.candleLightingOffset = prevOffset

                if (candleDate == null) {
                    log.warn("Candle lighting could not be computed for {}", dateIso)
                    return emptyList()
                }

                val inst = candleDate.toInstant()
                val utc = inst.atZone(ZoneId.of("UTC")).format(TS_FMT)
                val local = inst.atZone(zoneId).format(TS_FMT)

                log.info("Candle lighting: local={} utc={}", local, utc)
                listOf(
                    ShabbatTimeItem(
                        id = "candleLighting",
                        labelHe = "הדלקת נרות",
                        labelEn = "Candle Lighting",
                        utc = utc,
                        local = local,
                        instant = inst.toString()
                    )
                )
            }

            Calendar.SATURDAY -> {
                val tzaisDate: Date? = czc.tzais
                if (tzaisDate == null) {
                    log.warn("Tzais (Shabbat end) could not be computed for {}", dateIso)
                    return emptyList()
                }

                val inst = tzaisDate.toInstant()
                val utc = inst.atZone(ZoneId.of("UTC")).format(TS_FMT)
                val local = inst.atZone(zoneId).format(TS_FMT)

                log.info("Shabbat ends: local={} utc={}", local, utc)
                listOf(
                    ShabbatTimeItem(
                        id = "shabbatEnds",
                        labelHe = "צאת שבת",
                        labelEn = "Shabbat Ends",
                        utc = utc,
                        local = local,
                        instant = inst.toString()
                    )
                )
            }

            else -> emptyList()
        }
    }
}
