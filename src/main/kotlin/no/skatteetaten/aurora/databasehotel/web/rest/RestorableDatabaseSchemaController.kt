package no.skatteetaten.aurora.databasehotel.web.rest

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Date

data class RestorableDatabaseSchemaResource(
    val setToCooldownAt: Date,
    val deleteAfter: Date,

    val databaseSchema: DatabaseSchemaResource
)

@RestController
@RequestMapping("/api/v1/restorableSchema")
class RestorableDatabaseSchemaController(val databaseHotelService: DatabaseHotelService) {

    @GetMapping("/")
    fun findAll(@RequestParam(required = false) labels: String?): ResponseEntity<ApiResponse<*>> {

        val schemas = databaseHotelService.findAllDatabaseSchemasByCooldown(parseLabelsParam(labels))

        val resources = schemas
            .sortedBy { it.lastUsedOrCreatedDate }
            .map(DatabaseSchema::toRestorableDatabaseSchemaResource)
        return Responses.okResponse(resources)
    }

    @PostMapping("/{id}")
    fun restore(@PathVariable id: String) {

        val (schema, databaseInstance) = databaseHotelService.findSchemaById(id, false)
            ?: throw IllegalArgumentException("No such schema id=${id}")
        databaseInstance ?: throw java.lang.IllegalArgumentException("Schema id=${id} does not appear to be a managed schema")

        databaseInstance.restoreSchema(schema)
    }
}

private fun DatabaseSchema.toRestorableDatabaseSchemaResource(): RestorableDatabaseSchemaResource {
    val setToCooldownAt = this.setToCooldownAt
        ?: throw IllegalStateException("Schema [${this.id}] is in cooldown without a setToCooldownAt date")
    val deleteAfter =
        this.deleteAfter ?: throw IllegalStateException("Schema [${this.id}] is in cooldown without a deleteAfter date")
    return RestorableDatabaseSchemaResource(setToCooldownAt, deleteAfter, this.toResource())
}
