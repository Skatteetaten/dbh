package no.skatteetaten.aurora.databasehotel.web.security

import assertk.assertThat
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import assertk.assertions.isEqualTo as isEqualTo1

class SharedSecretReaderTest {

    @Test
    fun `Init secret throw exception given null values `() {
        assertThat {
            SharedSecretReader(null, null).secret
        }.isNotNull().isFailure().isInstanceOf(IllegalArgumentException::class)
    }

    @Test
    fun `Get secret given secret value`() {
        val sharedSecretReader = SharedSecretReader(null, "abc123")
        assertThat(sharedSecretReader.secret).isEqualTo1("abc123")
    }

    @Test
    fun `Get secret given secret location`() {
        val sharedSecretReader = SharedSecretReader("src/test/resources/aurora-token", null)
        assertThat(sharedSecretReader.secret).isEqualTo1("shared-secret")
    }

    @Test
    fun `Get secret given non-existing secret location throw exception`() {
        assertThat {
            SharedSecretReader("non-existing-path/secret.txt", null).secret
        }.isNotNull().isFailure().isInstanceOf(IllegalStateException::class)
    }
}
