package no.skatteetaten.aurora.databasehotel.web.rest

import io.micrometer.core.annotation.Timed
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelAdminService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DatabaseInstanceResource(
    val engine: DatabaseEngine,
    val host: String,
    val createSchemaAllowed: Boolean,
    val instanceName: String,
    val port: Int,
    val labels: Map<String, String>
)

@RestController
@RequestMapping("/api/v1/admin/databaseInstance")
class DatabaseInstanceController(private val databaseHotelAdminService: DatabaseHotelAdminService) {

    @GetMapping("/")
    @Timed
    fun findAll(): ResponseEntity<ApiResponse<*>> {

        val databaseInstances = databaseHotelAdminService.findAllDatabaseInstances()
        val resources = databaseInstances.map { it.metaInfo.toResource() }

        return Responses.okResponse(resources)
    }
}

fun DatabaseInstanceMetaInfo.toResource() =
    DatabaseInstanceResource(
        engine,
        host,
        createSchemaAllowed,
        instanceName,
        port,
        labels
    )
