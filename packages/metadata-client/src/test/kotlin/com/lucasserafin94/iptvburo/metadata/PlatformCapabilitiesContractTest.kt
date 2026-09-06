package com.lucasserafin94.iptvburo.metadata

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The platform-capabilities documents against their contract.
 *
 * `packages/contracts/platform-capabilities.schema.json` is the agreement every platform's
 * capabilities file is written against, and until now nothing in the build read the two together:
 * a required field could be renamed in a platform file and no test would notice. There is no JSON
 * Schema library on the classpath and none is added for this — the checks here are the ones the
 * schema actually uses: `required` fields are present, declared `properties` have the declared
 * `type`, and nested objects are held to the same rule recursively.
 *
 * Lives in this package because it already parses JSON with Gson and its tests run on the JVM;
 * the files are read by path relative to the repository root, as the schema and the documents are
 * not resources of any single module.
 */
class PlatformCapabilitiesContractTest {
    private val schema: JsonObject = JsonParser.parseString(repoFile(SCHEMA_PATH).readText()).asJsonObject

    @Test
    fun `windows-preview honours the capabilities contract`() {
        validate(schema, readDocument("windows-preview.json"), "windows-preview.json")
    }

    @Test
    fun `android-adaptive honours the capabilities contract`() {
        validate(schema, readDocument("android-adaptive.json"), "android-adaptive.json")
    }

    /**
     * The validator must not be vacuous: a document missing a required field, or holding one with
     * the wrong type, has to fail — otherwise the two tests above would pass on anything.
     */
    @Test
    fun `the validator itself catches a missing field and a wrong type`() {
        val valid = readDocument("android-adaptive.json")

        val missingField = valid.deepCopy().apply { remove("playback") }
        assertFailsWith<AssertionError> { validate(schema, missingField, "broken") }

        val wrongType =
            valid.deepCopy().apply {
                getAsJsonObject("navigation").addProperty("dpad", "yes")
            }
        assertFailsWith<AssertionError> { validate(schema, wrongType, "broken") }
    }

    private fun readDocument(name: String): JsonObject =
        JsonParser.parseString(repoFile("$DOCUMENTS_DIR/$name").readText()).asJsonObject

    private fun validate(
        schema: JsonObject,
        document: JsonObject,
        path: String,
    ) {
        val required = schema.getAsJsonArray("required")?.map(JsonElement::getAsString).orEmpty()
        for (field in required) {
            assertTrue(document.has(field), "$path: required field '$field' is missing")
        }
        val properties = schema.getAsJsonObject("properties") ?: return
        for ((name, propertySchemaElement) in properties.entrySet()) {
            val value = document.get(name) ?: continue
            val propertySchema = propertySchemaElement.asJsonObject
            val type = propertySchema.get("type")?.asString ?: continue
            assertType(value, type, "$path.$name")
            if (type == "object" && value.isJsonObject) {
                validate(propertySchema, value.asJsonObject, "$path.$name")
            }
            if (type == "array" && value.isJsonArray) {
                val itemType = propertySchema.getAsJsonObject("items")?.get("type")?.asString
                if (itemType != null) {
                    value.asJsonArray.forEachIndexed { index, item ->
                        assertType(item, itemType, "$path.$name[$index]")
                    }
                }
            }
        }
    }

    private fun assertType(
        value: JsonElement,
        type: String,
        path: String,
    ) {
        val matches =
            when (type) {
                "string" -> value.isJsonPrimitive && value.asJsonPrimitive.isString
                "boolean" -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
                "number" -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber
                "object" -> value.isJsonObject
                "array" -> value.isJsonArray
                else -> error("unsupported schema type '$type' at $path")
            }
        assertTrue(matches, "$path: expected $type, found $value")
    }

    private fun repoFile(relativePath: String): File {
        // Gradle runs tests with the module directory as the working directory.
        val file = File(relativePath)
        check(file.isFile) {
            "Cannot read $relativePath from ${File(".").absolutePath} — " +
                "the test expects to run from the metadata-client module directory."
        }
        return file
    }

    private companion object {
        const val SCHEMA_PATH = "../contracts/platform-capabilities.schema.json"
        const val DOCUMENTS_DIR = "../platform-capabilities"
    }
}
