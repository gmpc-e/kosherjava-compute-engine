package com.elad.halacha.rest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import kotlin.test.fail

object TestSupport {
    private val mapper = ObjectMapper()

    fun readGoldenResource(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: fail("Golden resource not found: $path")
        return stream.readAllBytes().toString(StandardCharsets.UTF_8)
    }

    fun assertJsonEqualsGolden(goldenPath: String, actualJson: String) {
        val expectedJson = readGoldenResource(goldenPath)
        val expected: JsonNode = mapper.readTree(expectedJson)
        val actual: JsonNode = mapper.readTree(actualJson)

        if (expected != actual) {
            fail(
                "JSON mismatch for $goldenPath\n" +
                "Expected:\n${expected.toPrettyString()}\n\n" +
                "Actual:\n${actual.toPrettyString()}"
            )
        }
    }
}
