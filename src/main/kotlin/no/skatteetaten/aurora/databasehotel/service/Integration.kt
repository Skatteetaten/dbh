package no.skatteetaten.aurora.databasehotel.service

import java.time.Duration

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema

interface Integration {

    fun onSchemaCreated(databaseSchema: DatabaseSchema)

    fun onSchemaDeleted(databaseSchema: DatabaseSchema, cooldownDuration: Duration)

    fun onSchemaUpdated(databaseSchema: DatabaseSchema)

    fun onSchemaReactivated(schema: DatabaseSchema)
}
