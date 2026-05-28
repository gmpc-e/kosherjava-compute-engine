package com.elad.halacha.rest.profiles

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object ProfileStore {
    private val log = LoggerFactory.getLogger("ProfileStore")
    private val mapper = jacksonObjectMapper()

    // key -> resource path (e.g., "profiles/shimon.json")
    private val resourceByKey = ConcurrentHashMap<String, String>()
    // key -> parsed Profile
    private val cache = ConcurrentHashMap<String, Profile>()

    init {
        loadIndex()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IndexEntry(val key: String, val displayName: String? = null)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class IndexWrapper(val profiles: List<IndexEntry> = emptyList())

    private fun loadResource(name: String) =
        Thread.currentThread().contextClassLoader?.getResourceAsStream(name)
            ?: javaClass.classLoader?.getResourceAsStream(name)

    private fun loadIndex() {
        val idxStream = loadResource("profiles/index.json")
        if (idxStream == null) {
            log.warn("profiles/index.json not found on classpath; no profiles will be exposed")
            return
        }
        val indexJson = idxStream.readAllBytes().toString(StandardCharsets.UTF_8)
        log.info("Raw index.json content length={}", indexJson.length)
        val entries: List<IndexEntry> = try {
            mapper.readValue<IndexWrapper>(indexJson).profiles
        } catch (e: Exception) {
            log.error("Failed parsing profiles/index.json: ${e.message}", e)
            emptyList()
        }
        log.info("Parsed {} profile entries from index.json", entries.size)

        entries.forEach { entry ->
            val filename = "${entry.key}.json"
            val path = "profiles/$filename"
            val stream = loadResource(path)
            if (stream == null) {
                log.warn("Profile file listed in index not found: {}", path)
                return@forEach
            }
            try {
                val json = stream.readAllBytes().toString(StandardCharsets.UTF_8)
                val profile: Profile = mapper.readValue(json)
                resourceByKey[profile.key] = path
                cache[profile.key] = profile
                log.info("Loaded profile '{}' from {}", profile.key, path)
            } catch (e: Exception) {
                log.error("Failed loading profile from {}: {}", path, e.message)
            }
        }
    }

    fun list(): List<Profile> = cache.values.sortedBy { it.key }

    fun get(key: String): Profile? = cache[key]
}
