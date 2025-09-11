package com.elad.halacha.rest

/**
 * Optional human-readable descriptions for select KosherJava methods.
 * Keep this list tiny; expand gradually. If a method isn't listed here,
 * /methods will return "description": null.
 */
object MethodDocs {
    private val map: Map<String, String> = mapOf(
        // ZmanimCalendar
        "getSunrise" to "Sunrise at the observer’s location (sea level).",
        "getSunset" to "Sunset at the observer’s location (sea level).",
        "getChatzos" to "Halachic midday (midpoint between sunrise and sunset).",
        "getMinchaGedola" to "Earliest time for Mincha (half hour after Chatzos).",
        "getMinchaKetana" to "Preferred time for Mincha (9.5 seasonal hours).",
        "getPlagHamincha" to "Plag Hamincha (1.25 seasonal hours before sunset).",

        // AstronomicalCalendar
        "getSeaLevelSunset" to "Sunset calculated at sea level, ignoring elevation.",

        // ComplexZmanimCalendar (examples)
        "getAlos60" to "Alot Hashachar fixed 60 minutes before sunrise.",
        "getAlos72" to "Alot Hashachar fixed 72 minutes before sunrise.",
        "getTzais" to "Nightfall based on standard fixed minutes (implementation-specific).",
        "getTzais72" to "Nightfall fixed 72 minutes after sunset."
    )

    fun describe(name: String): String? = map[name]
}