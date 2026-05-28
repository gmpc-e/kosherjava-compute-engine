package com.elad.halacha.engine.profiles

import com.elad.halacha.engine.compute.MethodRegistry
import com.elad.halacha.engine.compute.ZmanimComputer
import com.elad.halacha.engine.internal.InternalMethodRegistry
import com.elad.halacha.engine.model.ComputeMethod
import com.elad.halacha.engine.model.ComputeRequest
import com.elad.halacha.engine.calendar.ShabbatTimesComputer
import com.elad.halacha.profiles.api.*
import com.elad.halacha.profiles.store.ProfileStore
import com.elad.halacha.profiles.validate.ProfileValidator
import com.kosherjava.zmanim.util.GeoLocation
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

class ProfilesServiceImpl : ProfilesService {

    override fun listProfiles(): List<ProfileSummary> =
        ProfileStore.list().map { ProfileSummary(it.key, it.displayName, it.labels) }

    override fun getProfile(key: String): Profile? = ProfileStore.get(key)

    override fun computeProfile(key: String, input: ProfileComputeInput): ProfileComputeResponse {
        val stored = ProfileStore.get(key)
            ?: return ProfileComputeResponse(
                profile = MinimalProfileInfo(key, displayName = "unknown"),
                input = input,
                results = emptyList(),
                warnings = listOf(
                    ValidationWarning(
                        path = "$",
                        code = "profile_not_found",
                        message = "Profile '$key' not found"
                    )
                )
            )

        val validation = ProfileValidator.validate(stored)
        if (!validation.valid) {
            // Contract: on invalid profile, return empty results; surface errors as warnings.
            val warns = buildList {
                addAll(validation.warnings) // already List<ValidationWarning>
                addAll(validation.errors.map { e -> ValidationWarning(e.path, e.code, e.message) })
            }
            return ProfileComputeResponse(
                profile = MinimalProfileInfo(stored.key, stored.displayName, stored.labels),
                input = input,
                results = emptyList(),
                warnings = warns
            )
        }

        val zoneId = ZoneId.of(input.geo.tz)
        val gl = GeoLocation(
            "request",
            input.geo.lat,
            input.geo.lon,
            input.geo.elev,
            TimeZone.getTimeZone(zoneId)
        )
        val dateInstant = LocalDate.parse(input.dateIso).atStartOfDay(zoneId).toInstant()

        val results: List<ProfileComputeItem> = stored.times.map { t ->
            when (t.target.kind) {
                "EXTERNAL_NAME" -> {
                    val name = t.target.externalMethod!!
                    val req = ComputeRequest(
                        method = ComputeMethod.SUNSET, // ignored in by-name path
                        dateIso = input.dateIso,
                        lat = input.geo.lat,
                        lon = input.geo.lon,
                        elevationMeters = input.geo.elev,
                        tz = input.geo.tz
                    )
                    val r = ZmanimComputer.computeByExternalName(name, req)
                    val owner = MethodRegistry.resolve(name)?.owner

                    ProfileComputeItem(
                        id = t.id,
                        label = t.label,
                        resolution = Resolution(
                            kind = "EXTERNAL_NAME",
                            externalMethod = name,
                            owner = owner
                        ),
                        utc = r.utc,
                        local = r.local,
                        instant = r.instant?.toString()
                    )
                }

                "INTERNAL" -> {
                    val internalId = t.target.internalMethodId
                    if (internalId.isNullOrBlank()) {
                        ProfileComputeItem(
                            id = t.id,
                            label = t.label,
                            resolution = Resolution(
                                kind = "INTERNAL",
                                internalMethodId = null,
                                owner = "INTERNAL",
                                status = "missing_id"
                            )
                        )
                    } else {
                        val out = runCatching {
                            InternalMethodRegistry.compute(
                                idString = internalId,
                                date = Date.from(dateInstant),
                                loc = gl,
                                params = t.target.params ?: emptyMap()
                            )
                        }.getOrElse {
                            return@map ProfileComputeItem(
                                id = t.id,
                                label = t.label,
                                resolution = Resolution(
                                    kind = "INTERNAL",
                                    internalMethodId = internalId,
                                    owner = "INTERNAL",
                                    status = "error"
                                )
                            )
                        }

                        val dt = out.time
                        if (dt == null) {
                            ProfileComputeItem(
                                id = t.id,
                                label = t.label,
                                resolution = Resolution(
                                    kind = "INTERNAL",
                                    internalMethodId = internalId,
                                    owner = "INTERNAL",
                                    status = "unresolved"
                                )
                            )
                        } else {
                            val instant = dt.toInstant()
                            ProfileComputeItem(
                                id = t.id,
                                label = t.label,
                                resolution = Resolution(
                                    kind = "INTERNAL",
                                    internalMethodId = internalId,
                                    owner = "INTERNAL",
                                    status = "ok"
                                ),
                                utc = instant.atZone(ZoneId.of("UTC")).toString(),
                                local = instant.atZone(zoneId).toString(),
                                instant = instant.toString()
                            )
                        }
                    }
                }

                else -> ProfileComputeItem(
                    id = t.id,
                    label = t.label,
                    resolution = Resolution(
                        kind = t.target.kind,
                        status = "unsupported_kind"
                    )
                )
            }
        }

        val shabbatItems = ShabbatTimesComputer.compute(input.dateIso, gl, input.candleOffsetMinutes).map { s ->
            ProfileComputeItem(
                id = s.id,
                label = Labels(he = s.labelHe, en = s.labelEn),
                resolution = Resolution(kind = "SHABBAT", owner = "ShabbatTimesComputer", status = "ok"),
                utc = s.utc, local = s.local, instant = s.instant
            )
        }

        return ProfileComputeResponse(
            profile = MinimalProfileInfo(stored.key, stored.displayName, stored.labels),
            input = input,
            results = results + shabbatItems,
            warnings = validation.warnings
        )
    }
}