package com.elad.halacha.profiles.store

import com.elad.halacha.profiles.api.Labels
import com.elad.halacha.profiles.api.Profile
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.InputStream

/**
 * Classpath-backed store.
 * On Android, directory listing is not reliable; we therefore prefer a manifest:
 *   resources/profiles/index.json
 * We still keep a best-effort filesystem fallback for JVM/dev.
 */
object ProfileStore {

    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private const val PATH_PREFIX = "profiles/"
    private const val INDEX_NAME = "index.json"

    data class Entry(val key: String, val displayName: String, val labels: Labels?)

    private data class Index(val profiles: List<Entry> = emptyList())

    fun list(): List<Entry> {
        // 1) Preferred: read the manifest (works on Android & JVM)
        loadResource("${PATH_PREFIX}${INDEX_NAME}")?.use { ins ->
            return runCatching { mapper.readValue<Index>(ins) }
                .getOrElse { Index(emptyList()) }
                .profiles
        }

        // 2) Fallback (JVM/dev): try to list files in the folder
        val loader = Thread.currentThread().contextClassLoader
        val dirUrl = loader.getResource(PATH_PREFIX) ?: return emptyList()
        val dir = try { java.io.File(dirUrl.toURI()) } catch (_: Exception) { null } ?: return emptyList()
        val files = dir.listFiles { f -> f.extension == "json" && f.name != INDEX_NAME } ?: return emptyList()

        return files.mapNotNull { file ->
            runCatching {
                val p: Profile = mapper.readValue(file)
                Entry(p.key, p.displayName, p.labels)
            }.getOrNull()
        }
    }

    fun get(key: String): Profile? {
        // Always read the concrete JSON by key
        val res = "$PATH_PREFIX$key.json"
        loadResource(res)?.use { ins ->
            return runCatching { mapper.readValue<Profile>(ins) }.getOrNull()
        }
        return null
    }

    private fun loadResource(path: String): InputStream? {
        val cl = Thread.currentThread().contextClassLoader
        return cl.getResourceAsStream(path) ?: javaClass.classLoader?.getResourceAsStream(path)
    }
}