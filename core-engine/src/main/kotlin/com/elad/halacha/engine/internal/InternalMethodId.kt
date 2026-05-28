package com.elad.halacha.engine.internal

enum class InternalMethodId(val id: String, val display: String) {

    // ALOS / MISHEYAKIR / TZAIS
    SFORH_ALOS_72_ZMANIYOT_ASTRO(
        "SFARADI_OR_HACHAIM.ALOS_72_ZMANIYOT_ASTRO",
        "Alos by 72 zmaniyot minutes before sea-level sunrise"
    ),
    SFORH_MISHEYAKIR_SHAOT_ZMANIYOT_ASTRO(
        "SFARADI_OR_HACHAIM.MISHEYAKIR_SHAOT_ZMANIYOT_ASTRO",
        "Misheyakir by zmanit hours from sea-level sunrise (default −1.1h)"
    ),
    SFORH_TZAIS_VARIANTS_DEGREES_4_9(
        "SFARADI_OR_HACHAIM.TZAIS_VARIANTS_DEGREES_4_9",
        "Tzais by degrees (e.g., 4.9°)"
    ),

    // Sof zmanim (Sea-level Gra/Tanya; MGA expanded)
    SFORH_SOF_ZMAN_SHMA_GRA_ASTRO(
        "SFARADI_OR_HACHAIM.SOF_ZMAN_SHMA_GRA_ASTRO",
        "Sof Zman Kriat Shema (Gra/Tanya): 3 zmaniyot hours; day = Sea-level sunrise → Sea-level sunset"
    ),
    SFORH_SOF_ZMAN_TEFILLA_MGA_72_ZMANIYOT_ASTRO(
        "SFARADI_OR_HACHAIM.SOF_ZMAN_TEFILLA_MGA_72_ZMANIYOT_ASTRO",
        "Sof Zman Tefillah (MGA): 4 zmaniyot hours; day = Sea-level expanded ±72 zmaniyot"
    ),
    SFORH_SOF_ZMAN_TEFILLA_GRA_ASTRO(
        "SFARADI_OR_HACHAIM.SOF_ZMAN_TEFILLA_GRA_ASTRO",
        "Sof Zman Tefillah (Gra/Tanya): 4 zmaniyot hours; day = Sea-level sunrise → Sea-level sunset"
    ),
    SFORH_TZAIS_13P5_ZMANIYOT_ASTRO("SFARADI_OR_HACHAIM.TZAIS_13P5_ZMANIYOT_ASTRO",
        "Tzais +13.5 zmanit minutes (astro day)"),

    SFORH_TZAIS_FIXED_MINUTES_AFTER_SUNSET(
        "SFARADI_OR_HACHAIM.TZAIS_FIXED_MINUTES_AFTER_SUNSET",
        "Tzais: fixed minutes after sea-level sunset (default 18, configurable via minutesAfterSunset param)"
    ),

    // Generic (shita-neutral) methods
    GENERIC_TZAIS_BY_DEGREES(
        "GENERIC.TZAIS_BY_DEGREES",
        "Tzais by configurable degrees below horizon (set via astroDegrees param)"
    )
    ;

    companion object {
        fun from(id: String): InternalMethodId? = entries.firstOrNull { it.id == id }
    }
}