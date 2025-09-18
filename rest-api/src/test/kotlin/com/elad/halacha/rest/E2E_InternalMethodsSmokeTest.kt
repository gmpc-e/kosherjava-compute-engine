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

class E2E_InternalMethodsSmokeTest {

    private val mapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @Test
    fun `compute mixed profile returns internal and external times with non-null utc_local`() = testApplication {
        application { module() }

        // Load the test profile JSON from test resources
        val profileJson = this::class.java.classLoader
            .getResource("profiles/test-e2e.json")!!.readText()

        val resp: HttpResponse = client.post(
            "/profiles/compute?date=2025-09-16&lat=32.0871&lon=34.8875&elev=40&tz=Asia/Jerusalem"
        ) {
            contentType(ContentType.Application.Json)
            setBody(profileJson)
        }

        assertEquals(HttpStatusCode.OK, resp.status, "Unexpected HTTP status")
        val body = resp.bodyAsText()
        assertTrue(body.isNotBlank(), "Empty body")

        val root: JsonNode = mapper.readTree(body)
        val results = root.get("results")
        assertTrue(results.isArray && results.size() > 0, "results should be a non-empty array")

        // ensure we got at least one INTERNAL and one EXTERNAL_NAME item with non-null utc/local
        val hasInternal = results.any {
            val res = it.get("resolution")
            res?.get("kind")?.asText() == "INTERNAL" &&
                    !it.get("utc")?.asText(null).isNullOrBlank() &&
                    !it.get("local")?.asText(null).isNullOrBlank()
        }
        val hasExternal = results.any {
            val res = it.get("resolution")
            res?.get("kind")?.asText() == "EXTERNAL_NAME" &&
                    !it.get("utc")?.asText(null).isNullOrBlank() &&
                    !it.get("local")?.asText(null).isNullOrBlank()
        }

        assertTrue(hasInternal, "Expected at least one INTERNAL item with non-null utc/local")
        assertTrue(hasExternal, "Expected at least one EXTERNAL_NAME item with non-null utc/local")

        // warnings should be empty for a valid happy path
        if (root.has("warnings")) {
            val warnings = root.get("warnings")
            assertTrue(!warnings.isArray || warnings.size() == 0, "warnings should be empty")
        }
    }
}