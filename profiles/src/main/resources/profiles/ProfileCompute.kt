package com.elad.halacha.profiles

import com.elad.halacha.engine.model.ZmanimComputer
import com.elad.halacha.rest.profiles.Profile
import com.elad.halacha.rest.profiles.Target
import com.elad.halacha.rest.profiles.ProfileTime
import com.kosherjava.zmanim.util.GeoLocation
import java.time.ZoneId
import java.util.Date
import com.elad.halacha.engine.internal.InternalMethodRegistry

data class ComputeInputs(
    val date: Date,
    val zoneId: ZoneId,
    val geoLocation: GeoLocation
)

data class ComputedTime(
    val id: String,
    val label: com.elad.halacha.rest.profiles.Labels? = null,
    val utc: String?,
    val local_time: String?,
    val source: String,
    val warnings: List<String> = emptyList()
)

object ProfileCompute {

    fun compute(profile: Profile, inputs: ComputeInputs): List<ComputedTime> {
        return profile.times.map { t -> computeOne(t, inputs) }
    }

    private fun computeOne(t: ProfileTime, inputs: ComputeInputs): ComputedTime {
        return when (t.target.kind) {
            "EXTERNAL_NAME" -> {
                val method = t.target.externalMethod ?: return unresolved(t, "Missing externalMethod")
                val date = inputs.date
                val out = ZmanimComputer.computeByExternalName(method, date, inputs.geoLocation)
                val instant = out?.toInstant()
                val utc = instant?.atZone(ZoneId.of("UTC"))?.toString()
                val local = instant?.atZone(inputs.zoneId)?.toString()
                ComputedTime(
                    id = t.id, label = t.label,
                    utc = utc, local_time = local,
                    source = "EXTERNAL:$method"
                )
            }
            "INTERNAL" -> {
                val id = t.target.internalMethodId ?: return unresolved(t, "Missing internalMethodId")
                val params = t.target.params ?: emptyMap()
                val out = InternalMethodRegistry.compute(
                    idString = id,
                    date = inputs.date,
                    loc = inputs.geoLocation,
                    params = params
                )
                val instant = out.time?.toInstant()
                ComputedTime(
                    id = t.id, label = t.label,
                    utc = instant?.atZone(ZoneId.of("UTC"))?.toString(),
                    local_time = instant?.atZone(inputs.zoneId)?.toString(),
                    source = "INTERNAL:${out.id.id}"
                )
            }
            else -> unresolved(t, "Unsupported kind '${t.target.kind}'")
        }
    }

    private fun unresolved(t: ProfileTime, why: String): ComputedTime =
        ComputedTime(
            id = t.id, label = t.label,
            utc = null, local_time = null,
            source = "UNRESOLVED",
            warnings = listOf(why)
        )
}