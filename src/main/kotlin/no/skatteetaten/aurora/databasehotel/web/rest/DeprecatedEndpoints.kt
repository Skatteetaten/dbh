package no.skatteetaten.aurora.databasehotel.web.rest

import io.micrometer.core.annotation.Timed
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class DeprecatedEndpoints(
    private val databaseInstanceController: DatabaseInstanceController,
    private val databaseSchemaController: DatabaseSchemaController
) {

    @GetMapping("/admin/databaseInstance/")
    @Timed
    fun databaseInstanceControllerFindAll() = databaseInstanceController.findAll()

    @PostMapping("/admin/databaseInstance/{host}/deleteUnused")
    @Timed
    fun databaseInstanceControllerDeleteUnused(@PathVariable host: String) =
        databaseInstanceController.deleteUnused(host)

    @GetMapping("/schema/{id}")
    @Timed
    fun findById(@PathVariable id: String) = databaseSchemaController.findById(id)

    @DeleteMapping("/schema/{id}")
    @Timed
    fun deleteById(
        @PathVariable id: String,
        @RequestHeader(name = "cooldown-duration-hours", required = false) cooldownDurationHours: Long?
    ) = databaseSchemaController.deleteById(id, cooldownDurationHours)

    @GetMapping("/schema/")
    @Timed
    fun findAll(
        @RequestParam(name = "engine", defaultValue = "filter-by") engineName: String,
        @RequestParam(required = false, defaultValue = "") labels: String,
        @RequestParam(name = "q", required = false) query: String?
    ) = databaseSchemaController.findAll(engineName, labels, query)

    @PostMapping("/schema/")
    @Timed
    fun create(@RequestBody schemaCreationRequest: SchemaCreationRequest) =
        databaseSchemaController.create(schemaCreationRequest)
}
