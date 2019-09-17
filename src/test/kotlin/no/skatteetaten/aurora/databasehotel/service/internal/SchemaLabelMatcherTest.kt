package no.skatteetaten.aurora.databasehotel.service.internal

import assertk.assertThat
import assertk.assertions.isEqualTo
import java.util.stream.Stream
import no.skatteetaten.aurora.databasehotel.domain.DomainUtils
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.of as args
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource

class SchemaLabelMatcherTest {

    class Params : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            args(emptyMap<String, String>(), mapOf("deploymentId" to "DEPID"), false),
            args(mapOf("deploymentId" to "DEPID"), emptyMap<String, String>(), true),
            args(mapOf("deploymentId" to "DEPID"), mapOf("deploymentId" to "DEPID"), true),
            args(mapOf("deploymentId" to "DEPID"), mapOf("deploymentId" to "DEPI"), false),
            args(mapOf("deploymentId" to "DEPID"), mapOf("deploymentId" to "DEPID", "other" to "SOMETHING"), false)
        )
    }

    private val databaseSchema = DomainUtils.createDatabaseSchema()

    @ParameterizedTest
    @ArgumentsSource(Params::class)
    fun `verify label matching`(
        schemaLabels: Map<String, String>,
        searchLabels: Map<String, String>,
        expectedMatch: Boolean
    ) {

        databaseSchema.labels = schemaLabels
        assertThat(SchemaLabelMatcher.matchesAll(databaseSchema, searchLabels)).isEqualTo(expectedMatch)
    }
}
