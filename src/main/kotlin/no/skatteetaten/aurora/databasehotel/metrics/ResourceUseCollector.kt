package no.skatteetaten.aurora.databasehotel.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.AFFILIATION
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.APPLICATION
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.DATABASE_ENGINE
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.DATABASE_HOST
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.ENVIRONMENT
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.NAMESPACE
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.SCHEMA_ID
import no.skatteetaten.aurora.databasehotel.metrics.ResourceUseCollector.Tags.SCHEMA_TYPE
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.HashMap

@Component
class ResourceUseCollector(
    private val databaseHotelService: DatabaseHotelService,
    private val registry: MeterRegistry
) {
    private val schemaSizeValues = mutableMapOf<String, SchemaSizeValue>()

    private val gauges = mutableMapOf<String, Gauge>()

    @Scheduled(fixedDelayString = "\${metrics.resourceUseCollectInterval}", initialDelay = 5000)
    fun collectResourceUseMetrics() {
        val start = System.currentTimeMillis()
        LOG.info("Collecting resource use metrics")
        val allSchemas = databaseHotelService.findAllDatabaseSchemas(null, HashMap(), true)
        LOG.debug("Found {} schemas total", allSchemas.size)
        val databaseSchemas = allSchemas
            .filter { it.labels.containsEveryKey(NAMESPACE_LABEL, APP_LABEL, AFFILIATION_LABEL) }
            .filter { it.active }

        // Let's update the SchemaSizeValues that backs the schema size gauges
        databaseSchemas.forEach { schema ->
            val existingValue = schemaSizeValues[schema.id]
            if (existingValue != null) {
                existingValue.sizeBytes = schema.sizeMb
            } else {
                val value = SchemaSizeValue(schema.id, schema.sizeMb)
                schemaSizeValues[schema.id] = value
                registerGaugeForSchema(schema, value)
            }
        }

        // Remove the SchemaSizeValues that does not currently have a DatabaseSchema object. This indicates that
        // the DatabaseSchema has been deleted since the last time we updated the gauges. Deleting the value that
        // backs a Gauge *should* also in fact delete the Gauge since the reference to the SchemaSizeValue is a
        // WeakReference. This does not appear to happen (bug?(!)), so we have to remove the gauge from the registry
        // manually.
        schemaSizeValues.keys.removeIf { schemaId: String ->
            val isAnExistingSchema = databaseSchemas.any { schemaId == it.id }
            val isSchemaRemovedSinceLastIteration = !isAnExistingSchema
            if (isSchemaRemovedSinceLastIteration) registry.remove(gauges[schemaId]!!)
            isSchemaRemovedSinceLastIteration
        }
        LOG.info(
            "Resource use metrics collected for {} schemas in {} millis", databaseSchemas.size,
            System.currentTimeMillis() - start
        )
    }

    private fun registerGaugeForSchema(schema: DatabaseSchema, value: SchemaSizeValue) {
        val gauge = Gauge.builder(METRIC_NAME, value, { it.sizeBytes * 1024 * 1024 })
            .baseUnit("bytes")
            .description("the size of the schema")
            .tags(createLabelsArray(schema))
            .register(registry)
        gauges[schema.id] = gauge
    }

    private fun createLabelsArray(schema: DatabaseSchema): List<Tag> {
        val labels = schema.labels
        val t = { tag: Tags, value: String? -> Tag.of(tag.name2, value ?: "UNKNOWN") }
        val namespace = labels[NAMESPACE_LABEL]?.removeSuffix("-")
        val affiliation = labels[AFFILIATION_LABEL]
        val environment = affiliation?.let { namespace?.getEnvironment(it) }
        return listOf(
            t(SCHEMA_ID, schema.id),
            t(AFFILIATION, affiliation),
            t(APPLICATION, labels[APP_LABEL]),
            t(ENVIRONMENT, environment),
            t(NAMESPACE, namespace),
            t(DATABASE_HOST, schema.databaseInstanceMetaInfo.host),
            t(DATABASE_ENGINE, schema.databaseInstanceMetaInfo.engine.name),
            t(SCHEMA_TYPE, schema.type.name)
        )
    }

    data class SchemaSizeValue(var id: String, var sizeBytes: Double)

    enum class Tags(val name2: String) {
        SCHEMA_ID("id"),
        SCHEMA_TYPE("schemaType"),
        DATABASE_HOST("databaseHost"),
        DATABASE_ENGINE("databaseEngine"),
        AFFILIATION("affiliation"),
        ENVIRONMENT("environment"),
        APPLICATION("application"),
        NAMESPACE("namespace")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ResourceUseCollector::class.java)
        private const val METRIC_NAME = "aurora.dbh.schema.size.bytes"
        private const val NAMESPACE_LABEL = "environment"
        private const val APP_LABEL = "application"
        private const val AFFILIATION_LABEL = "affiliation"
    }

    private fun String.getEnvironment(affiliation: String): String {
        val environment = this.replace("^$affiliation".toRegex(), "").removePrefix("-")
        return if (environment.isBlank()) affiliation else environment
    }
}

private fun <K> Map<K, *>.containsEveryKey(vararg keys: K) = this.keys.containsAll(setOf(*keys))

