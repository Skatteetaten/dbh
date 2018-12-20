package no.skatteetaten.aurora.databasehotel.web.rest

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.net.URLEncoder.encode
import java.util.stream.Stream

class DatabaseSchemaControllerTest2 {

    class Params : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            "affiliation=aurora,application=test1" to mapOf("affiliation" to "aurora", "application" to "test1"),
            "affiliation=aurora , application" to mapOf("affiliation" to "aurora", "application" to null),
            "affiliation= aurora , application = " to mapOf("affiliation" to "aurora", "application" to null),
            "affiliation= aurora ,=something" to mapOf("affiliation" to "aurora"),
            "affiliation,application" to mapOf("affiliation" to null, "application" to null),
            "" to emptyMap()
        ).map { Arguments.of(it.first, it.second) }
    }

    @ParameterizedTest
    @ArgumentsSource(Params::class)
    fun verifyLabelsParsing(labelString: String, expectedLabels: Map<String, String>) {
        assert(parseLabelsParam(encode(labelString, "UTF-8"))).isEqualTo(expectedLabels)
    }
}
