package no.skatteetaten.aurora.databasehotel.web.rest

import io.micrometer.core.annotation.Timed
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.User
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstanceRequirements
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.UnsupportedEncodingException
import java.lang.String.format
import java.net.URLDecoder
import java.time.Duration
import java.util.Date

data class SchemaMetadataResource(val sizeInMb: Double?)

data class DatabaseSchemaResource(
    val id: String,
    val type: String,
    val jdbcUrl: String,
    val name: String,
    val createdDate: Date,
    val lastUsedDate: Date?,
    val databaseInstance: DatabaseInstanceResource,
    val users: List<UserResource>,
    val labels: Map<String, String>,
    val metadata: SchemaMetadataResource
)

data class SchemaCreationRequest(
    val engine: DatabaseEngine = DatabaseEngine.ORACLE,
    val instanceName: String? = null,
    val labels: Map<String, String>? = null,
    val schema: Schema? = null
)

data class Schema(val username: String, val password: String, val jdbcUrl: String)

@RestController
@RequestMapping("/api/v1/schema")
class DatabaseSchemaController(
    private val databaseHotelService: DatabaseHotelService,
    @Value("\${database-config.schemaListingAllowed}") private val schemaListingAllowed: Boolean,
    @Value("\${database-config.dropAllowed}") private val dropAllowed: Boolean
) {

    @GetMapping("/{id}")
    @Timed
    fun findById(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {

        val databaseSchema = databaseHotelService.findSchemaById(id)
            .map { it.left }
            .orElseThrow { IllegalArgumentException(format("No such schema %s", id)) }
        return Responses.okResponse(databaseSchema.toResource())
    }

    @DeleteMapping("/{id}")
    @Timed
    fun deleteById(
        @PathVariable id: String,
        @RequestHeader(name = "cooldown-duration-hours", required = false) cooldownDurationHours: Long?
    ): ResponseEntity<ApiResponse<*>> {

        if (!dropAllowed) {
            throw OperationDisabledException("Schema deletion has been disabled for this instance")
        }

        val cooldownDuration = cooldownDurationHours?.let { Duration.ofHours(it) }
        databaseHotelService.deleteSchemaById(id, cooldownDuration)
        return Responses.okResponse()
    }

    @GetMapping("/")
    @Timed
    fun findAll(
        @RequestParam(required = false, defaultValue = "") labels: String,
        @RequestParam(required = false) q: String?
    ): ResponseEntity<ApiResponse<*>> {

        if (!schemaListingAllowed) {
            throw OperationDisabledException("Schema listing has been disabled for this instance")
        }
        val schemas: Set<DatabaseSchema> = when {
            q == "for-deletion" -> databaseHotelService.findAllDatabaseSchemasForDeletion()
            labels.isBlank() -> databaseHotelService.findAllDatabaseSchemas()
            else -> databaseHotelService.findAllDatabaseSchemasByLabels(parseLabelsParam(labels))
        }

        val resources = schemas
            .sortedBy { it.lastUsedOrCreatedDate }
            .map(DatabaseSchema::toResource)
        return Responses.okResponse(resources)
    }

    @PutMapping("/{id}")
    @Timed
    fun update(
        @PathVariable id: String,
        @RequestBody schemaCreationRequest: SchemaCreationRequest
    ): ResponseEntity<ApiResponse<*>> {

        val labels = schemaCreationRequest.labels ?: emptyMap()
        val schema = schemaCreationRequest.schema
        val databaseSchema = when (schema) {
            null -> databaseHotelService.updateSchema(id, labels)
            else -> databaseHotelService.updateSchema(id, labels, schema.username, schema.jdbcUrl, schema.password)
        }
        return Responses.okResponse(databaseSchema.toResource())
    }

    @PostMapping("/")
    @Timed
    fun create(@RequestBody schemaCreationRequest: SchemaCreationRequest): ResponseEntity<ApiResponse<*>> {

        val labels = schemaCreationRequest.labels ?: emptyMap()
        val schema = schemaCreationRequest.schema
        val databaseSchema = if (schema == null) {
            val instanceRequirements = DatabaseInstanceRequirements(
                databaseEngine = schemaCreationRequest.engine,
                instanceName = schemaCreationRequest.instanceName
            )
            databaseHotelService.createSchema(instanceRequirements, labels)
        } else databaseHotelService.registerExternalSchema(schema.username, schema.password, schema.jdbcUrl, labels)
        return Responses.okResponse(databaseSchema.toResource())
    }
}

fun DatabaseSchema.toResource() = DatabaseSchemaResource(
    id = id,
    type = type.toString(),
    jdbcUrl = jdbcUrl,
    name = name,
    createdDate = createdDate,
    lastUsedDate = lastUsedDate,
    databaseInstance = databaseInstanceMetaInfo.toResource(),
    users = users.map(User::toResource),
    labels = labels,
    metadata = SchemaMetadataResource(metadata?.sizeInMb)
)

internal fun parseLabelsParam(labelsParam: String): Map<String, String?> {

    val labelsDecoded: String = try {
        URLDecoder.decode(labelsParam, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        throw IllegalArgumentException(e)
    }

    if (labelsDecoded.isBlank()) return emptyMap()

    val labelsUnparsed = labelsDecoded.splitRemoveEmpties(",")

    return labelsUnparsed.mapNotNull {
        val nameAndValue = it.splitRemoveEmpties("=")
        val name = nameAndValue.firstOrNull()?.emptyToNull() ?: return@mapNotNull null
        val value = nameAndValue.takeIf { it.size > 1 }?.get(1)
        Pair(name, value)
    }.toMap()
}

fun String.splitRemoveEmpties(delimiter: String) =
    split(delimiter)
        .dropLastWhile { it.isEmpty() }
        .map { it.trim() }

fun String.emptyToNull(): String? = if (isEmpty()) null else this