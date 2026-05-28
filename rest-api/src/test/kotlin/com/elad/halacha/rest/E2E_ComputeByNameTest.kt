package com.elad.halacha.rest

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class E2E_ComputeByNameTest {
    @Test
    fun seaLevelSunset_telaviv_golden() = testApplication {
        application { module() }

        val resp = client.get("/compute/by-name") {
            url {
                parameters.append("method", "getSeaLevelSunset")
                parameters.append("date", "2025-09-09")
                parameters.append("lat", "32.0853")
                parameters.append("lon", "34.7818")
                parameters.append("elev", "34")
                parameters.append("tz", "Asia/Jerusalem")
            }
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()

        TestSupport.assertJsonEqualsGolden(
            "golden/byname_seaLevelSunset_telaviv_2025-09-09.json",
            body
        )
    }
}
