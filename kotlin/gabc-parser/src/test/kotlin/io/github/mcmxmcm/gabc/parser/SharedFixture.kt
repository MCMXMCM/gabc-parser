package io.github.mcmxmcm.gabc.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Scores whose events and neumes a content compiler and the native engraver agree on: each with the number of its
 * events, and the number of notes in each of its neumes. Read from the Swift package's fixtures (`gabc.fixtures`).
 */
data class SharedFixture(
    val name: String,
    val scoreId: String,
    val gabc: String,
    val eventCount: Int,
    val neumeSizes: List<Int>,
) {
    companion object {
        fun all(): List<SharedFixture> {
            val directory = checkNotNull(System.getProperty("gabc.fixtures")) { "gabc.fixtures names the Swift package's fixtures" }
            val fixtures = Json.parseToJsonElement(File(directory, "gabc-engraving-fixtures.json").readText()).jsonArray
            return fixtures.map { element ->
                val fixture = element.jsonObject
                SharedFixture(
                    name = fixture.getValue("name").jsonPrimitive.content,
                    scoreId = fixture.getValue("scoreID").jsonPrimitive.content,
                    gabc = fixture.getValue("gabc").jsonPrimitive.content,
                    eventCount = fixture.getValue("eventCount").jsonPrimitive.int,
                    neumeSizes = fixture.getValue("neumeSizes").jsonArray.map { it.jsonPrimitive.int },
                )
            }
        }
    }
}
