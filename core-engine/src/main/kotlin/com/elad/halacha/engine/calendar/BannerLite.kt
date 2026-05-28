package com.elad.halacha.engine.calendar

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.round

// -----------------------------
// Simple POC models (internal)
// -----------------------------
data class BannerLiteRequest(
    val dateIso: String,   // "YYYY-MM-DD" in tz
    val lat: Double,
    val lon: Double,
    val elev: Double? = null,
    val tz: String,        // IANA tz, e.g. "Asia/Jerusalem"
    val profileKey: String // kept for future wiring; unused in this POC
)

enum class BannerLiteOccasionCode {
    SHABBAT,          // שבת
    EREV_SHABBAT,     // ערב שבת
    CANDLE_SHABBAT,   // הדלקת נרות (שבת)
    EREV_YOMTOV,      // ערב חג
    CANDLE_YOMTOV,    // הדלקת נרות יום טוב
    NONE
}

data class BannerLiteOccasion(
    val code: BannerLiteOccasionCode,
    val labelHe: String
)

data class BannerLiteZmaniyot(
    val method: String,       // "GRA" (POC)
    val shaahSeconds: Long,   // rounded seconds
    val minuteSeconds: Long   // rounded shaahSeconds / 60
)

data class BannerLiteResponse(
    val occasion: BannerLiteOccasion,
    val zmaniyot: BannerLiteZmaniyot,
    val warnings: List<String> = emptyList(),
    val candleLightingLocal: String? = null   // "HH:mm" (local tz) or null
)

// -----------------------------
// Service (self-contained POC)
// -----------------------------
interface BannerLiteService {
    fun compute(req: BannerLiteRequest): BannerLiteResponse
}

class BannerLiteServiceImpl : BannerLiteService {
    private val log = LoggerFactory.getLogger("com.elad.halacha.engine.calendar.BannerLite")

    override fun compute(req: BannerLiteRequest): BannerLiteResponse {
        val tz = ZoneId.of(req.tz)
        val localNoon = LocalDate.parse(req.dateIso).atTime(12, 0).atZone(tz)
        val civilDate: Date = Date.from(localNoon.toInstant())

        log.info(
            "BannerLite.compute start date={} tz={} lat={} lon={} elev={} profileKey={}",
            req.dateIso, req.tz, req.lat, req.lon, req.elev, req.profileKey
        )

        val geo = GeoLocation(
            "banner-lite",
            req.lat,
            req.lon,
            (req.elev ?: 0.0),
            TimeZone.getTimeZone(tz)   // pass TimeZone (not String)
        )
        val czc = ComplexZmanimCalendar(geo).apply { calendar.time = civilDate }

        val warnings = mutableListOf<String>()

        val sunrise = czc.sunrise
        val sunset  = czc.sunset
        if (sunrise == null || sunset == null) {
            if (sunrise == null) warnings += "SUNRISE_MISSING"
            if (sunset  == null) warnings += "SUNSET_MISSING"
            val resp = BannerLiteResponse(
                occasion = classifyOccasion(localNoon.toLocalDate(), tz),
                zmaniyot = BannerLiteZmaniyot(method = "GRA", shaahSeconds = 0, minuteSeconds = 0),
                warnings = warnings,
                candleLightingLocal = null
            )
            log.info("BannerLite.compute done (missing sun events) resp={}", resp)
            return resp
        }

        // --- Shaah Zmanit (POC = GRA): (sunset - sunrise) / 12
        val ms = (sunset.time - sunrise.time) / 12.0
        val shaahSec = round(ms / 1000.0).toLong().coerceAtLeast(0)
        val minuteSec = round(shaahSec / 60.0).toLong()

        // --- Candle lighting time (POC default = 18 minutes before sunset)
        val candleWarnings = mutableListOf<String>()
        val candleOffsetMinutes: Double = 18.0 // must be Double
        val candleDate: Date? = runCatching {
            val prev: Double = czc.candleLightingOffset
            czc.candleLightingOffset = candleOffsetMinutes
            val d = czc.candleLighting
            czc.candleLightingOffset = prev
            d
        }.onFailure {
            candleWarnings += "CANDLE_COMPUTE_ERROR"
        }.getOrNull()

        if (candleDate != null) candleWarnings += "CANDLE_DEFAULT_18M"

        // --- Occasion (IL default for POC)
        val occasion = classifyOccasion(localNoon.toLocalDate(), tz)

        val allWarnings = warnings + candleWarnings
        val resp = BannerLiteResponse(
            occasion = occasion,
            zmaniyot = BannerLiteZmaniyot(method = "GRA", shaahSeconds = shaahSec, minuteSeconds = minuteSec),
            warnings = allWarnings,
            candleLightingLocal = fmtHHmm(candleDate, tz)
        )

        log.info(
            "BannerLite.compute done occasion={} shaahSec={} minuteSec={} candle={} warnings={}",
            resp.occasion.code, shaahSec, minuteSec, resp.candleLightingLocal, allWarnings
        )
        return resp
    }

    /**
     * POC occasion classification:
     * - Friday  -> CANDLE_SHABBAT (for banner friendliness)
     * - Saturday-> SHABBAT
     * - Else if tomorrow is Yom Tov -> CANDLE_YOMTOV
     * - Else NONE
     */
    private fun classifyOccasion(date: LocalDate, tz: ZoneId): BannerLiteOccasion {
        val cal = GregorianCalendar.from(date.atStartOfDay(tz))
        val jc = JewishCalendar(cal).apply { isUseModernHolidays = true }

        return when (jc.dayOfWeek) {
            Calendar.SATURDAY -> BannerLiteOccasion(BannerLiteOccasionCode.SHABBAT, "שבת")
            Calendar.FRIDAY   -> BannerLiteOccasion(BannerLiteOccasionCode.CANDLE_SHABBAT, "הדלקת נרות")
            else -> {
                val tomorrow = cal.clone() as java.util.Calendar
                tomorrow.add(Calendar.DATE, 1)
                val jcTomorrow = JewishCalendar(tomorrow).apply { isUseModernHolidays = true }
                if (jcTomorrow.isYomTov) {
                    BannerLiteOccasion(BannerLiteOccasionCode.CANDLE_YOMTOV, "הדלקת נרות יום טוב")
                } else {
                    BannerLiteOccasion(BannerLiteOccasionCode.NONE, "—")
                }
            }
        }
    }

    private fun fmtHHmm(d: Date?, zone: ZoneId): String? =
        d?.toInstant()
            ?.atZone(zone)
            ?.let { "%02d:%02d".format(it.hour, it.minute) }
}