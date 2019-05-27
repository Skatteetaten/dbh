package no.skatteetaten.aurora.databasehotel.service

import java.math.BigDecimal

data class SchemaSize(val owner: String, val schemaSizeMb: BigDecimal)

interface ResourceUsageCollector {

    val schemaSizes: List<SchemaSize>

    fun getSchemaSize(schemaName: String): SchemaSize?
}
