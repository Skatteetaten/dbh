package no.skatteetaten.aurora.databasehotel.dao

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import java.util.stream.Stream
import org.junit.jupiter.params.provider.Arguments.of as args

class DataSourceUtilsTest {
    class Params : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            args(null, ""),
            args("", ""),
            args("AAA", "***"),
            args("1234567", "*******"),
            args("12345678", "12****78"),
            args("12345678ABCDEFG", "12***********FG")
        )
    }

    @ParameterizedTest
    @ArgumentsSource(Params::class)
    fun `generate password hint`(password: String?, expectedHint: String) {
        assertThat(DataSourceUtils.createPasswordHint(password)).isEqualTo(expectedHint)
    }
}
