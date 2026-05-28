package com.elad.halacha.engine.internal

import com.kosherjava.zmanim.util.GeoLocation
import java.util.Date

object SfaradiOrHachaim {

    data class Params(
        val astroDegrees: Double? = null,
        val misheyakirOffsetHoursFromSeaLevelSunrise: Double? = null,
        val minutesAfterSunset: Double? = null
    )

    fun compute(
        id: InternalMethodId,
        date: Date,
        loc: GeoLocation,
        params: Params = Params()
    ): InternalMethodComputer.Output {
        val inputs = InternalMethodComputer.Inputs(
            date = date,
            location = loc,
            astroDegrees = params.astroDegrees ?: 18.0
        )

        val time: Date? = when (id) {

            // ALOS / MISHEYAKIR / TZAIS
            InternalMethodId.SFORH_ALOS_72_ZMANIYOT_ASTRO ->
                InternalMethodComputer.alot72ZmaniyotSea(inputs)

            InternalMethodId.SFORH_MISHEYAKIR_SHAOT_ZMANIYOT_ASTRO ->
                InternalMethodComputer.misheyakirShaotZmaniyotSea(
                    inputs,
                    hoursFromSeaLevelSunrise = params.misheyakirOffsetHoursFromSeaLevelSunrise ?: -1.1
                )

            InternalMethodId.SFORH_TZAIS_VARIANTS_DEGREES_4_9 ->
                InternalMethodComputer.tzaisByDegrees(inputs, degrees = inputs.astroDegrees)

            // Sof zmanim
            InternalMethodId.SFORH_SOF_ZMAN_SHMA_GRA_ASTRO ->
                InternalMethodComputer.sofZmanShmaGraSea(inputs)

            InternalMethodId.SFORH_SOF_ZMAN_TEFILLA_MGA_72_ZMANIYOT_ASTRO ->
                InternalMethodComputer.sofZmanTefillaMgaSeaExpanded(inputs)

            InternalMethodId.SFORH_SOF_ZMAN_TEFILLA_GRA_ASTRO ->
                InternalMethodComputer.sofZmanTefillaGraSea(inputs)

            InternalMethodId.SFORH_TZAIS_13P5_ZMANIYOT_ASTRO ->
                InternalMethodComputer.tzais13p5ZmaniyotAstro(inputs)

            InternalMethodId.SFORH_TZAIS_FIXED_MINUTES_AFTER_SUNSET ->
                InternalMethodComputer.tzaisFixedMinutesAfterSunset(
                    inputs,
                    minutes = params.minutesAfterSunset ?: 18.0
                )
        }

        return InternalMethodComputer.Output(id, time)
    }
}