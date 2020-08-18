package no.skatteetaten.aurora.databasehotel.web.security

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException

private val logger = KotlinLogging.logger {}

/**
 * Component for reading the shared secret used for authentication. You may specify the shared secret directly using
 * the aurora.token.value property, or specify a file containing the secret with the aurora.token.location property.
 */
@Component
class SharedSecretReader(
    @Value("\${aurora.authentication.token.location:}") private val secretLocation: String?,
    @Value("\${aurora.authentication.token.value:}") private val secretValue: String?
) {

    val secret: String by lazy {
        initSecret(secretValue)
    }

    private fun initSecret(secretValue: String?): String {
        if (!secretValue.isNullOrBlank()) return secretValue
        if (secretLocation.isNullOrEmpty()) throw IllegalArgumentException("Either aurora.token.location or aurora.token.value must be specified")

        val secretFile = File(secretLocation).absoluteFile
        return try {
            logger.info("Reading token from file {}", secretFile.absolutePath)
            secretFile.readText()
        } catch (e: IOException) {
            throw IllegalStateException("Unable to read shared secret from specified location [${secretFile.absolutePath}]")
        }
    }
}
