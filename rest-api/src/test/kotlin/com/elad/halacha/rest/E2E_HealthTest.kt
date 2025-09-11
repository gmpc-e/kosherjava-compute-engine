package com.elad.halacha.rest

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class E2E_HealthTest {
    @Test
    fun health_ok() = testApplication {
        application { module() }

        val resp = client.get("/health")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("\"status\":\"ok\""))
        assertTrue(body.contains("KosherJava Compute Engine"))
    }
}
