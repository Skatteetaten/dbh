package no.skatteetaten.aurora.databasehotel.domain

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import no.skatteetaten.aurora.databasehotel.DomainUtils
import org.junit.jupiter.api.Test

class DatabaseSchemaTest {

    @Test
    fun `removes existing user when adding`() {
        val schema = DomainUtils.createDatabaseSchema()

        assertThat(schema.users).isEmpty()

        schema.addUser(User("ID", "A", "-", "SCHEMA"))
        schema.addUser(User("ID", "B", "-", "SCHEMA"))

        assertThat(schema.users).hasSize(2)

        schema.addUser(User("ID", "A", "PASS", "SCHEMA"))

        assertThat(schema.users).hasSize(2)
        assertThat(schema.users.first { it.name == "A" }.password).isEqualTo("PASS")
    }
}