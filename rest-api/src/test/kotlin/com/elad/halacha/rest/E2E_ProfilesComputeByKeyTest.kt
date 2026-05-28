package com.elad.halacha.rest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class E2E_ProfilesComputeByKeyTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

    @Test
    fun `profiles chazon-shamaim compute - basic contract holds`() = testApplication {
        application { module() }

        val resp: HttpResponse = client.get(
            "/profiles/chazon-shamaim/compute?date=2025-09-09&lat=32.0853&lon=34.7818&elev=34&tz=Asia/Jerusalem&lang=he"
        )

        assertEquals(HttpStatusCode.OK, resp.status, "Unexpected HTTP status")
        val body = resp.bodyAsText()
        assertTrue(body.isNotBlank(), "Empty body")

        val root: JsonNode = mapper.readTree(body)

        // Top-level fields
        assertTrue(root.has("profile"), "Missing 'profile'")
        assertTrue(root.has("input"), "Missing 'input'")
        assertTrue(root.has("results"), "Missing 'results'")

        // Profile
        val profile = root.get("profile")
        assertEquals("chazon-shamaim", profile.get("key").asText(), "profile.key mismatch")

        // Input
        val input = root.get("input")
        assertEquals("2025-09-09", input.get("dateIso").asText(), "input.dateIso mismatch")

        // Results
        val results = root.get("results")
        assertTrue(results.isArray && results.size() > 0, "results should be a non-empty array")

        // Find at least one computed EXTERNAL_NAME item with non-null times
        val anyComputed = results.any {
            val res = it.get("resolution")
            val kind = res?.get("kind")?.asText()
            val local = it.get("local")?.asText(null)
            val utc = it.get("utc")?.asText(null)
            kind == "EXTERNAL_NAME" && !local.isNullOrBlank() && !utc.isNullOrBlank()
        }
        assertTrue(anyComputed, "Expected at least one EXTERNAL_NAME item with non-null local/utc times")

        // Warnings (acceptable: missing or empty)
        if (root.has("warnings")) {
            val warnings = root.get("warnings")
            assertTrue(!warnings.isArray || warnings.size() == 0, "warnings should be empty for this happy path")
        }
    }
}