package no.skatteetaten.aurora.databasehotel.service

import java.math.BigDecimal
import no.skatteetaten.aurora.databasehotel.dao.Schema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema.Type
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema.Type.MANAGED
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData
import no.skatteetaten.aurora.databasehotel.domain.User

data class DatabaseSchemaBuilder(
    private val metaInfo: DatabaseInstanceMetaInfo,
    private val jdbcUrlBuilder: JdbcUrlBuilder,
    val users: List<SchemaUser>,
    val schemas: List<Schema>,
    val labels: List<Label>,
    val schemaSizes: List<SchemaSize>
) {
    private val schemaIndex = schemas.map { it.username to it }.toMap()
    private val userIndex = users.map { it.schemaId!! to it }.toMap()
    private val labelIndex = labels.groupBy { it.schemaId!! }
    private val schemaSizeIndex = schemaSizes.map { it.owner to it }.toMap()

    fun createMany(schemaDataList: List<SchemaData>, type: Type = MANAGED) = schemaDataList
        .filter { schemaIndex.containsKey(it.name) }
        .map { createOne(it, type) }.toSet()

    fun createOne(schemaData: SchemaData, type: Type = MANAGED): DatabaseSchema {

        val schema = schemaIndex[schemaData.name]
            ?: error("Missing SchemaData for Schema ${schemaData.name}")

        val schemaUsers = listOfNotNull(userIndex[schemaData.id])
        val schemaLabels = labelIndex[schemaData.id] ?: emptyList()
        val schemaSize = schemaSizeIndex[schema.username]

        return createOne(schemaData, schema, schemaUsers, schemaLabels, schemaSize, type)
    }

    private fun createOne(
        schemaData: SchemaData,
        schema: Schema,
        users: List<SchemaUser>,
        labels: List<Label>,
        schemaSize: SchemaSize?,
        type: Type = MANAGED
    ): DatabaseSchema {

        val jdbcUrl = jdbcUrlBuilder.create(metaInfo.host, metaInfo.port, schema.username)

        val databaseSchema = DatabaseSchema(
            schemaData.id,
            schemaData.active,
            metaInfo,
            jdbcUrl,
            schema.username,
            schemaData.createdDate,
            schema.lastLogin,
            schemaData.setToCooldownAt,
            schemaData.deleteAfter,
            createMetaData(schemaSize = schemaSize ?: SchemaSize(schema.username, BigDecimal.ZERO)),
            type
        )
        users.forEach { databaseSchema.addUser(User(it.id!!, it.username!!, it.password!!, it.type!!)) }
        databaseSchema.labels = labels.map { it.name!! to it.value }.toMap()

        return databaseSchema
    }

    companion object {

        @JvmStatic
        private fun createMetaData(schemaSize: SchemaSize?): DatabaseSchemaMetaData =
            DatabaseSchemaMetaData(schemaSize?.schemaSizeMb?.toDouble() ?: 0.0)
    }
}
