package no.skatteetaten.aurora.databasehotel.web.rest

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import java.net.URLEncoder.encode

class DatabaseSchemaControllerTest2 {

    @Test
    fun verifyLabelsParsing() {

        assert(parseLabelsParam(encode("affiliation=aurora,application=test1", "UTF-8")))
            .isEqualTo(mapOf("affiliation" to "aurora", "application" to "test1"))

        assert(parseLabelsParam(encode("affiliation=aurora , application", "UTF-8")))
            .isEqualTo(mapOf("affiliation" to "aurora", "application" to null))

        assert(parseLabelsParam(encode("affiliation= aurora , application = ", "UTF-8")))
            .isEqualTo(mapOf("affiliation" to "aurora", "application" to null))

        assert(parseLabelsParam(encode("affiliation= aurora ,=something", "UTF-8")))
            .isEqualTo(mapOf("affiliation" to "aurora"))
    }
}