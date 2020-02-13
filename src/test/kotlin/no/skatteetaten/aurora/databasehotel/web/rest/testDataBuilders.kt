package no.skatteetaten.aurora.databasehotel.web.rest

import io.mockk.mockk
import java.util.Date
import no.skatteetaten.aurora.databasehotel.DatabaseEngine
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance

data class DatabaseSchemaTestBuilder(val type: DatabaseSchema.Type = DatabaseSchema.Type.MANAGED) {
    fun build() = DatabaseSchema(
        id = "123",
        active = true,
        databaseInstanceMetaInfo = DatabaseInstanceMetaInfo(
            DatabaseEngine.POSTGRES,
            "instanceName",
            "host",
            8080,
            true,
            emptyMap()
        ),
        jdbcUrl = "jdbcUrl",
        name = "name",
        createdDate = Date(),
        lastUsedDate = null,
        setToCooldownAt = Date(),
        deleteAfter = Date(),
        metadata = null,
        type = type
    )
}

class DatabaseInstanceBuilder {
    fun build() = DatabaseInstance(
        DatabaseInstanceMetaInfo(
            DatabaseEngine.ORACLE,
            "test",
            "dbhost.example.com",
            1521,
            true,
            mapOf("affiliation" to "aurora")
        ),
        mockk(),
        mockk(),
        mockk(),
        mockk(),
        6, 1
    )
}
