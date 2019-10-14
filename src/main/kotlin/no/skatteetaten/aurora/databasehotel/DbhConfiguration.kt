package no.skatteetaten.aurora.databasehotel

import mu.KotlinLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "database-config")
@Component
class DbhConfiguration {
    private lateinit var _databases: List<Map<String, Any>>
    var databases: List<Map<String, Any>>
        get() = _databases
        // Spring will set this from configuration.
        set(value) {
            validateConfig(value)
            logger.info("Using databases [{}]", value.joinToString { db -> db["host"] as String })
            _databases = value
        }

    private companion object {
        val requiredParams = arrayOf("username", "password", "instanceName", "host")

        fun validateConfig(databaseConfigs: List<Map<String, Any>>) =
            databaseConfigs.forEachIndexed { i, db ->
                requiredParams
                    .filter { !db.containsKey(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { throw ConfigException("Required configuration parameter(s) missing (${it.joinToString()}) in configuration of database with index [$i]") }
            }
    }
}
