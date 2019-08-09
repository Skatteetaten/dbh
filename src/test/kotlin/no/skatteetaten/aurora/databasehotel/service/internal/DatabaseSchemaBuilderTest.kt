package no.skatteetaten.aurora.databasehotel.service.internal

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import java.util.Date
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DomainUtils.metaInfo
import no.skatteetaten.aurora.databasehotel.service.SchemaSize
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleJdbcUrlBuilder
import org.junit.jupiter.api.Test

class DatabaseSchemaBuilderTest {

    private val builder = DatabaseSchemaBuilder(
        metaInfo("test", "localhost", 1521, true),
        OracleJdbcUrlBuilder("none")
    )

    @Test
    fun `returns empty list when no schema data is specified`() {

        val schemas = builder.createMany()
        assertThat(schemas).isEmpty()
    }

    @Test
    fun `combines data correctly`() {

        val schemas = builder.createMany(
            listOf(SchemaData("A", name = "SCHEMA_NAME")),
            listOf(Schema("SCHEMA_NAME", Date(), Date()))
        )

        assertThat(schemas).hasSize(1)
        with(schemas.first()) {
            assertThat(::name).isEqualTo("SCHEMA_NAME")
            assertThat(::id).isEqualTo("A")
            assertThat(::createdDate).isNotNull()
        }
    }

    @Test
    fun `skips schemas with missing schema data`() {

        val schemas = builder.createMany(
            listOf(SchemaData("A", name = "SCHEMA_NAME")),
            listOf(
                Schema("SCHEMA_NAME", Date(), Date()),
                Schema("SCHEMA_NAME_WITH_NO_MATCH", Date(), Date())
            )
        )

        assertThat(schemas).hasSize(1)
        with(schemas.first()) {
            assertThat(::name).isEqualTo("SCHEMA_NAME")
            assertThat(::id).isEqualTo("A")
            assertThat(::createdDate).isNotNull()
        }
    }

    private fun DatabaseSchemaBuilder.createMany(
        schemaDataList: List<SchemaData> = emptyList(),
        schemas: List<Schema> = emptyList(),
        users: List<SchemaUser> = emptyList(),
        labels: List<Label> = emptyList(),
        schemaSizes: List<SchemaSize> = emptyList()
    ): Set<DatabaseSchema> = this.createMany(schemaDataList, schemas, users, labels, schemaSizes)
}
