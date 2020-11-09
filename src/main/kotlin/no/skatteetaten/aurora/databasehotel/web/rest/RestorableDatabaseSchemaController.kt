package no.skatteetaten.aurora.databasehotel.web.rest

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Date

data class RestorableDatabaseSchemaResource(
    val setToCooldownAt: Date,
    val deleteAfter: Date,

    val databaseSchema: DatabaseSchemaResource
)

data class RestoreDatabaseSchemaPayload(
    val active: Boolean
)

@RestController
@RequestMapping("/api/v1/restorableSchema")
class RestorableDatabaseSchemaController(val databaseHotelService: DatabaseHotelService) {

    @GetMapping("/")
    fun findAll(@RequestParam(required = false) labels: String?): ResponseEntity<ApiResponse<*>> {

        val schemas = databaseHotelService.findAllInactiveDatabaseSchemas(parseLabelsParam(labels))

        val resources = schemas
            .sortedByDescending { it.lastUsedOrCreatedDate }
            .map(DatabaseSchema::toRestorableDatabaseSchemaResource)
        return Responses.okResponse(resources)
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: String): ResponseEntity<ApiResponse<*>> {
        val schema = databaseHotelService.findSchemaById(id, active = false)?.first
        val schemaResource = schema?.let { listOf(it.toResource()) }
        return Responses.okResponse(schemaResource.orEmpty())
    }

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody payload: RestoreDatabaseSchemaPayload
    ): ResponseEntity<ApiResponse<*>> {

        require(payload.active) { "Property active in RestoreDatabaseSchemaPayload has to be true, schema id=$id" }

        val (schema, databaseInstance) = databaseHotelService.findSchemaById(id, false)
            ?: throw IllegalArgumentException("No such schema id=$id")
        databaseInstance ?: throw java.lang.IllegalArgumentException("Schema id=$id is not a managed schema")
        databaseInstance.reactivateSchema(schema)
        val activatedSchema = databaseHotelService.findSchemaById(id)?.first ?: throw IllegalArgumentException("No such activated schema with id=$id")
        return Responses.okResponse(activatedSchema.toResource())
    }
}

private fun DatabaseSchema.toRestorableDatabaseSchemaResource(): RestorableDatabaseSchemaResource {
    val setToCooldownAt = this.setToCooldownAt
        ?: throw IllegalStateException("Schema [${this.id}] is in cooldown without a setToCooldownAt date")
    val deleteAfter =
        this.deleteAfter ?: throw IllegalStateException("Schema [${this.id}] is in cooldown without a deleteAfter date")
    return RestorableDatabaseSchemaResource(setToCooldownAt, deleteAfter, this.toResource())
}
