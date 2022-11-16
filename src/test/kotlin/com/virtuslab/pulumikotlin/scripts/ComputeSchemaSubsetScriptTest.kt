package com.virtuslab.pulumikotlin.scripts

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.test.assertContains

internal class ComputeSchemaSubsetScriptTest {
    @Test
    fun `should find the type itself`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolvedAwsClassicSchemaPath(),
            name = "aws:fsx/getOpenZfsSnapshotFilter:getOpenZfsSnapshotFilter",
            context = "type",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            types = setOf(
                "aws:fsx/getOpenZfsSnapshotFilter:getOpenZfsSnapshotFilter",
            ),
        )
    }

    @Test
    fun `should find subset when using resource (that references some types)`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolvedAwsClassicSchemaPath(),
            name = "aws:lambda/function:Function",
            context = "resource",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            types = setOf(
                "aws:lambda/Runtime:Runtime",
                "aws:lambda/FunctionDeadLetterConfig:FunctionDeadLetterConfig",
                "aws:lambda/FunctionEnvironment:FunctionEnvironment",
                "aws:lambda/FunctionEphemeralStorage:FunctionEphemeralStorage",
                "aws:lambda/FunctionFileSystemConfig:FunctionFileSystemConfig",
                "aws:lambda/FunctionImageConfig:FunctionImageConfig",
                "aws:lambda/FunctionTracingConfig:FunctionTracingConfig",
                "aws:lambda/FunctionVpcConfig:FunctionVpcConfig",
            ),
            resources = setOf("aws:lambda/function:Function"),
        )
    }

    @Test
    fun `should find subset when using function (that references some types)`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolvedAwsClassicSchemaPath(),
            name = "aws:fsx/getOpenZfsSnapshot:getOpenZfsSnapshot",
            context = "function",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            types = setOf("aws:fsx/getOpenZfsSnapshotFilter:getOpenZfsSnapshotFilter"),
            functions = setOf("aws:fsx/getOpenZfsSnapshot:getOpenZfsSnapshot"),
        )
    }

    @Test
    fun `should work when using type (that is referenced by some function)`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolvedAwsClassicSchemaPath(),
            name = "aws:fsx/getOpenZfsSnapshotFilter:getOpenZfsSnapshotFilter",
            context = "type",
            loadFullParents = "true",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            types = setOf("aws:fsx/getOpenZfsSnapshotFilter:getOpenZfsSnapshotFilter"),
            functions = setOf("aws:fsx/getOpenZfsSnapshot:getOpenZfsSnapshot"),
        )
    }

    @Test
    fun `should work even when there are key conflicts`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolve(SCHEMA_PATH_AZURE_NATIVE_SUBSET_WITH_IP_ALLOCATION),
            name = "azure-native:network:IPAllocationMethod",
            context = "type",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            types = setOf("azure-native:network:IPAllocationMethod"),
        )
    }

    @Test
    fun `should work even when there are recursive references (regression)`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolve(SCHEMA_PATH_AZURE_NATIVE_SUBSET_WITH_RECURSION),
            name = "azure-native:batch:AutoScaleRunResponse",
            context = "type",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            types = setOf(
                "azure-native:batch:AutoScaleRunResponse",
                "azure-native:batch:AutoScaleRunErrorResponse",
            ),
        )
    }

    @Test
    fun `should load full parents when --load-full-parents=true`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolve(SCHEMA_PATH_AWS_SUBSET_FOR_COMPUTE),
            name = "aws:lambda/FunctionTracingConfig:FunctionTracingConfig",
            context = "type",
            loadFullParents = "true",
        )

        val decodedOutputSchema = json.decodeFromString<Schema>(outputSchema)

        assertContainsOnly(
            decodedOutputSchema,
            resources = setOf("aws:lambda/function:Function"),
            types = setOf(
                "aws:lambda/FunctionDeadLetterConfig:FunctionDeadLetterConfig",
                "aws:lambda/FunctionEnvironment:FunctionEnvironment",
                "aws:lambda/FunctionEphemeralStorage:FunctionEphemeralStorage",
                "aws:lambda/FunctionFileSystemConfig:FunctionFileSystemConfig",
                "aws:lambda/FunctionImageConfig:FunctionImageConfig",
                "aws:lambda/FunctionTracingConfig:FunctionTracingConfig",
                "aws:lambda/FunctionVpcConfig:FunctionVpcConfig",
                "aws:lambda/Runtime:Runtime",
            ),
        )
    }

    @Test
    fun `should shorten descriptions when --shorten-descriptions=true`() {
        val outputSchema = runComputeSchemaSubsetScript(
            schemaPath = resolve(SCHEMA_PATH_AWS_SUBSET_FOR_COMPUTE),
            name = "aws:lambda/function:Function",
            context = "resource",
            shortenDescriptions = "true",
        )

        assertContains(
            outputSchema,
            "S3 key of an object containing the function<<shortened>> Conflicts with `filename` and `image_uri`.",
        )
    }

    private fun resolve(relativePath: String) =
        Paths.get(relativePath).absolutePathString()

    private fun resolvedAwsClassicSchemaPath() =
        resolve(SCHEMA_PATH_AWS_SUBSET_FOR_COMPUTE)

    private fun assertContainsOnly(
        schema: Schema,
        functions: Set<String> = emptySet(),
        resources: Set<String> = emptySet(),
        types: Set<String> = emptySet(),
    ) {
        assertAll(
            { assertEquals(types, schema.types.keys) },
            { assertEquals(resources, schema.resources.keys) },
            { assertEquals(functions, schema.functions.keys) },
        )
    }

    private fun runComputeSchemaSubsetScript(
        schemaPath: String,
        name: String,
        context: String,
        shortenDescriptions: String? = null,
        loadFullParents: String? = null,
    ): String {
        val outputStream = ByteArrayOutputStream()

        outputStream.use {
            val regularArguments = listOf(
                "--schema-path",
                schemaPath,
                "--name",
                name,
                "--context",
                context,
            )
            val optionalArgumentsToBeFlattened = listOfNotNull(
                toListOrNull("--shorten-descriptions", shortenDescriptions),
                toListOrNull("--load-full-parents", loadFullParents),
            )

            ComputeSchemaSubsetScript(it).main(
                regularArguments + optionalArgumentsToBeFlattened.flatten(),
            )
        }

        return outputStream.toByteArray().decodeToString()
    }

    private fun toListOrNull(vararg strings: String?): List<String>? {
        if (strings.any { it == null }) {
            return null
        }
        return strings.map { requireNotNull(it) }.toList()
    }

    @Serializable
    private data class Schema(
        val types: Map<String, JsonElement>,
        val resources: Map<String, JsonElement>,
        val functions: Map<String, JsonElement>,
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }
}

private const val SCHEMA_PATH_AZURE_NATIVE_SUBSET_WITH_IP_ALLOCATION =
    "src/test/resources/schema-azure-native-3.44.2-subset-with-ip-allocation.json"
private const val SCHEMA_PATH_AZURE_NATIVE_SUBSET_WITH_RECURSION =
    "src/test/resources/schema-azure-native-3.44.2-subset-with-recursion.json"
private const val SCHEMA_PATH_AWS_SUBSET_FOR_COMPUTE =
    "src/test/resources/schema-aws-classic-5.16.2-subset-for-compute-schema-subset-script-test.json"