package com.elad.halacha.rest

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class E2E_MethodsTest {
    private val mapper = ObjectMapper()

    @Test
    fun methods_top10_matches_golden() = testApplication {
        application { module() }

        val resp = client.get("/methods")
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = resp.bodyAsText()
        val arr = mapper.readTree(body)
        val top10 = mapper.createArrayNode().addAll(arr.elements().asSequence().take(10).toList())

        TestSupport.assertJsonEqualsGolden(
            "golden/methods_top10.json",
            top10.toString()
        )
    }
}
