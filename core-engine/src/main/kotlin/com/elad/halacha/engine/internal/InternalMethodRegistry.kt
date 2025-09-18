package com.elad.halacha.engine.internal

import com.kosherjava.zmanim.util.GeoLocation
import org.slf4j.LoggerFactory
import java.util.Date

object InternalMethodRegistry {

    data class Descriptor(val id: String, val display: String, val shita: String)

    private val log = LoggerFactory.getLogger(InternalMethodRegistry::class.java)

    val all: List<Descriptor> = InternalMethodId.entries.map {
        Descriptor(it.id, it.display, it.id.substringBefore('.'))
    }

    fun compute(
        idString: String,
        date: Date,
        loc: GeoLocation,
        params: Map<String, Any?> = emptyMap()
    ): InternalMethodComputer.Output {
        val id = InternalMethodId.from(idString)
            ?: error("Unknown internal method id: $idString")

        val astroDegrees = (params["astroDegrees"] as? Number)?.toDouble()
        val misheyakirHours = (params["misheyakirOffsetHoursFromSeaLevelSunrise"] as? Number)?.toDouble()
            ?: run {
                val legacyMinutes = (params["misheyakirOffsetMinutesFromAstroSunrise"] as? Number)?.toDouble()
                if (legacyMinutes != null) {
                    log.warn("Param 'misheyakirOffsetMinutesFromAstroSunrise' is deprecated; use 'misheyakirOffsetHoursFromSeaLevelSunrise'. Converting {} min → {} hours.",
                        legacyMinutes, legacyMinutes / 60.0)
                }
                legacyMinutes?.div(60.0)
            }

        return when (id) {
            InternalMethodId.SFORH_ALOS_72_ZMANIYOT_ASTRO,
            InternalMethodId.SFORH_MISHEYAKIR_SHAOT_ZMANIYOT_ASTRO,
            InternalMethodId.SFORH_TZAIS_VARIANTS_DEGREES_4_9,
            InternalMethodId.SFORH_SOF_ZMAN_SHMA_GRA_ASTRO,
            InternalMethodId.SFORH_SOF_ZMAN_TEFILLA_MGA_72_ZMANIYOT_ASTRO,
            InternalMethodId.SFORH_SOF_ZMAN_TEFILLA_GRA_ASTRO -> {
                SfaradiOrHachaim.compute(
                    id = id,
                    date = date,
                    loc = loc,
                    params = SfaradiOrHachaim.Params(
                        astroDegrees = astroDegrees,
                        misheyakirOffsetHoursFromSeaLevelSunrise = misheyakirHours
                    )
                )
            }
        }
    }
}