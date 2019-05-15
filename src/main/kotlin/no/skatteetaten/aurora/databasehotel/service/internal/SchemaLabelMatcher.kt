package no.skatteetaten.aurora.databasehotel.service.internal

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema

object SchemaLabelMatcher {

    fun findAllMatchingSchemas(schemas: Set<DatabaseSchema>, labels: Map<String, String?>): Set<DatabaseSchema> =
        schemas.filter { schema -> matchesAll(schema, labels) }.toSet()

    internal fun matchesAll(schema: DatabaseSchema, labels: Map<String, String?>?): Boolean =
        labels?.all { (key, valueToMatch) -> valueToMatch == schema.labels[key] } ?: true
}
