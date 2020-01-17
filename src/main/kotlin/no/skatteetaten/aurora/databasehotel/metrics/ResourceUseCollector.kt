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
    private val schemaGauges = mutableMapOf<String, Pair<GaugeValue, Gauge>>()
    private val schemaCountGauges = mutableMapOf<String, Pair<GaugeValue, Gauge>>()

    @Scheduled(fixedDelayString = "\${metrics.resourceUseCollectInterval}", initialDelay = 5000)
    fun collectResourceUseMetrics() {
        val start = System.currentTimeMillis()

        val databaseSchemas = loadDatabaseSchemas()
        registerOrUpdateMetrics(databaseSchemas)
        removeMetricsForDeletedSchemas(databaseSchemas)
        removeMetricsForRemovedLabels(databaseSchemas)

        LOG.info(
            "Resource use metrics collected for {} schemas in {} millis", databaseSchemas.size,
            System.currentTimeMillis() - start
        )
    }

    private fun loadDatabaseSchemas(): List<DatabaseSchema> {
        LOG.info("Collecting resource use metrics")
        val databaseSchemas = databaseHotelService.findAllDatabaseSchemas(null, HashMap(), true)
            .filter { it.labels.containsEveryKey(NAMESPACE_LABEL, APP_LABEL, AFFILIATION_LABEL) }
        LOG.debug("Found {} schemas total", databaseSchemas.size)
        return databaseSchemas
    }

    private fun registerOrUpdateMetrics(databaseSchemas: List<DatabaseSchema>) {
        databaseSchemas.forEach { schema ->
            val existing = schemaGauges[schema.id]
            if (existing != null) {
                val metricData = existing.first
                metricData.value = schema.sizeMb * 1024 * 1024
            } else {
                val data = GaugeValue(schema.id, schema.sizeMb)
                val gauge = registerSchemaSizeGauge(schema, data)
                schemaGauges[schema.id] = data to gauge
            }
        }
        val grouped: Map<Map<String, String>, List<DatabaseSchema>> = groupDatabaseSchemas(databaseSchemas)
        grouped.forEach { (groupKeys, schemas) ->
            val id = groupKeys.toSortedMap().map { (k, v) -> "$k=$v" }.joinToString(",")
            val data = GaugeValue(id, schemas.size.toDouble())
            val gague = registerSchemaSizeGaugeRename(groupKeys, data)
            schemaCountGauges[id] = data to gague
        }
    }

    private fun groupDatabaseSchemas(databaseSchemas: List<DatabaseSchema>): Map<Map<String, String>, List<DatabaseSchema>> {
        return databaseSchemas.groupBy {
            mapOf(
                "state" to if (it.active) "ACTIVE" else "COOLDOWN",
                "databaseHost" to it.databaseInstanceMetaInfo.host,
                "databaseEngine" to it.databaseInstanceMetaInfo.engine.name,
                "schemaType" to it.type.name,
                "affiliation" to (it.labels[AFFILIATION_LABEL] ?: "UNKNOWN_AFFILIATION")
            )
        }
    }

    /**
     * Remove the SchemaSizeValues that does not currently have a DatabaseSchema object. This indicates that
     * the DatabaseSchema has been deleted since the last time we updated the gauges. Deleting the value that
     * backs a Gauge *should* also in fact delete the Gauge since the reference to the SchemaSizeValue is a
     * WeakReference. This does not appear to happen (bug?(!)), so we have to remove the gauge from the registry
     * manually.
     */
    private fun removeMetricsForDeletedSchemas(databaseSchemas: List<DatabaseSchema>) {
        val schemasToRemove = schemaGauges.keys
            .filter { schemaId: String -> !databaseSchemas.any { schemaId == it.id } }

        schemasToRemove
            .mapNotNull { schemaGauges[it] }
            .forEach { (data, gauge) ->
                schemaGauges.remove(data.id)
                registry.remove(gauge.id)
            }
    }

    private fun removeMetricsForRemovedLabels(databaseSchemas: List<DatabaseSchema>) {
        val grouped = groupDatabaseSchemas(databaseSchemas)
        val sortedMapOfTruth = grouped.forEach { (groupKeys) ->
            groupKeys.toSortedMap().map { (k, v) -> "$k=$v" }.joinToString(",")
        }

        val schemasToRemove = schemaCountGauges.keys
            .filter { id: String -> !sortedMapOfTruth.equals(id) }

        schemasToRemove
            .mapNotNull { schemaCountGauges[it] }
            .forEach { (data, gauge) ->
                schemaCountGauges.remove(data.id)
                registry.remove(gauge.id)
            }
    }

    private fun registerSchemaSizeGauge(schema: DatabaseSchema, value: GaugeValue): Gauge {
        return Gauge.builder(SCHEMA_SIZE_METRIC_NAME, value, { it.value })
            .baseUnit("bytes")
            .description("the size of the schema")
            .tags(createLabelsArray(schema))
            .register(registry)
    }

    private fun registerSchemaSizeGaugeRename(
        groupKeys: Map<String, String?>,
        value: GaugeValue
    ): Gauge {
        val tags = groupKeys.map { (k, v) -> Tag.of(k, v ?: "UNKNOWN") }
        return Gauge.builder(RENAME_THIS_METRIC_NAME, value, { it.value })
            .baseUnit("count")
            .description("The amount of schemas")
            .tags(tags)
            .register(registry)
    }

    private fun createLabelsArray(schema: DatabaseSchema): List<Tag> {
        val labels = schema.labels
        val t = { tag: Tags, value: String? -> Tag.of(tag.tagName, value ?: "UNKNOWN") }
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

    data class GaugeValue(var id: String, var value: Double)

    enum class Tags(val tagName: String) {
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
        private const val SCHEMA_SIZE_METRIC_NAME = "aurora.dbh.schema.size.bytes"
        private const val RENAME_THIS_METRIC_NAME = "aurora.dbh.schema.rename.me"
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
