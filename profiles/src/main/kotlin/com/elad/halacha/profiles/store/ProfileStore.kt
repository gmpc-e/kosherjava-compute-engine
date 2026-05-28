package com.elad.halacha.profiles.store

import com.elad.halacha.profiles.api.Labels
import com.elad.halacha.profiles.api.Profile
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

object ProfileStore {
    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private const val PATH_PREFIX = "profiles/"

    data class Entry(val key: String, val displayName: String, val labels: Labels?)

    fun list(): List<Entry> {
        val loader = Thread.currentThread().contextClassLoader
        val dirUrl = loader.getResource(PATH_PREFIX) ?: return emptyList()
        val dir = File(dirUrl.toURI())
        if (!dir.exists()) return emptyList()

        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val p: Profile = mapper.readValue(file)
                    Entry(p.key, p.displayName, p.labels)
                } catch (_: Exception) {
                    null
                }
            }
            ?: emptyList()
    }

    fun get(key: String): Profile? {
        val loader = Thread.currentThread().contextClassLoader
        val url = loader.getResource("$PATH_PREFIX$key.json") ?: return null
        val txt = url.readText()
        return mapper.readValue(txt)
    }
}