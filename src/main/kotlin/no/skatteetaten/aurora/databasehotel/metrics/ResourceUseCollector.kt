package no.skatteetaten.aurora.databasehotel.metrics

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.AFFILIATION
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.APPLICATION
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.DATABASE_ENGINE
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.DATABASE_HOST
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.ENVIRONMENT
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.INSTANCE_NAME
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.NAMESPACE
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.SCHEMA_ID
import no.skatteetaten.aurora.databasehotel.metrics.MetricTag.SCHEMA_TYPE
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService
import no.skatteetaten.aurora.databasehotel.service.DatabaseInstance
import no.skatteetaten.aurora.databasehotel.service.TablespaceInfo
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
    private val availibleTablespacesGauges = mutableMapOf<String, Pair<GaugeValue, Gauge>>()

    @Scheduled(fixedDelayString = "\${metrics.resourceUseCollectInterval}", initialDelay = 5000)
    fun collectResourceUseMetrics() {

        LOG.info("Collecting resource use metrics")
        val start = System.currentTimeMillis()

        val databaseSchemas = loadDatabaseSchemas()
        val tablespaceInfo = databaseHotelService.getTablespaceInfo()

        handleSchemaSizeMetrics(databaseSchemas)
        handleSchemaCountMetrics(databaseSchemas)
        handleAvailibleTablespacesMetrics(tablespaceInfo)

        LOG.info(
            "Resource use metrics collected for {} schemas in {} millis", databaseSchemas.size,
            System.currentTimeMillis() - start
        )
    }

    private fun loadDatabaseSchemas() =
        databaseHotelService.findAllDatabaseSchemas(null, HashMap(), true)
            .toList()
            .also { LOG.debug("Found {} schemas total", it.size) }

    private fun handleSchemaSizeMetrics(databaseSchemas: List<DatabaseSchema>) {
        val currentMetricIds = databaseSchemas.map { schema ->
            val id = schema.id
            val existing = schemaGauges[id]
            if (existing != null) {
                val metricData = existing.first
                metricData.value = schema.sizeMb * 1024 * 1024
            } else {
                val data = GaugeValue(id, schema.sizeMb)
                val gauge = registerSchemaSizeGauge(schema, data)
                schemaGauges[id] = data to gauge
            }
            id
        }

        schemaGauges.removeDeprecatedMetrics(currentMetricIds)
    }

    private fun handleSchemaCountMetrics(databaseSchemas: List<DatabaseSchema>) {
        val grouped: Map<CountGroup, List<DatabaseSchema>> = groupDatabaseSchemasForCounting(databaseSchemas)
        val currentMetricIds: List<String> = grouped.map { (groupKeys, schemas) ->
            val id = groupKeys.id
            val existing = schemaCountGauges[id]
            if (existing != null) {
                val metricData = existing.first
                metricData.value = schemas.size.toDouble()
            } else {
                val data = GaugeValue(id, schemas.size.toDouble())
                val gauge = registerSchemaCountGauge(groupKeys, data)
                schemaCountGauges[id] = data to gauge
            }
            id
        }

        schemaCountGauges.removeDeprecatedMetrics(currentMetricIds)
    }

    private fun handleAvailibleTablespacesMetrics(tablespaceInfo: List<Pair<DatabaseInstance, TablespaceInfo>>) {
        val currentMetricIds: List<String> = tablespaceInfo.map { (instance, tablespaceInfo) ->
            val id = instance.metaInfo.host
            val existing = availibleTablespacesGauges[id]
            if (existing != null) {
                val metricData = existing.first
                metricData.value = tablespaceInfo.available.toDouble()
            } else {
                val data = GaugeValue(id, tablespaceInfo.available.toDouble())
                val gauge = registerAvailibleTablespacesGauge(instance, data)
                availibleTablespacesGauges[id] = data to gauge
            }
            id
        }
        availibleTablespacesGauges.removeDeprecatedMetrics(currentMetricIds)
    }

    private fun groupDatabaseSchemasForCounting(databaseSchemas: List<DatabaseSchema>) =
        databaseSchemas.groupBy {
            mapOf(
                "state" to if (it.active) "ACTIVE" else "COOLDOWN",
                "databaseHost" to it.databaseInstanceMetaInfo.host,
                "databaseEngine" to it.databaseInstanceMetaInfo.engine.name,
                "schemaType" to it.type.name,
                "affiliation" to it.labels[AFFILIATION_LABEL]
            )
        }

    private fun registerSchemaSizeGauge(schema: DatabaseSchema, value: GaugeValue): Gauge {
        return Gauge.builder(SCHEMA_SIZE_METRIC_NAME, value, { it.value })
            .baseUnit("bytes")
            .description("the size of the schema")
            .tags(schema.metricTags)
            .register(registry)
    }

    private fun registerSchemaCountGauge(groupKeys: CountGroup, value: GaugeValue): Gauge {
        val tags = groupKeys.map { (k, v) -> tagOfValueOrUnknown(k, v) }
        return Gauge.builder(SCHEMA_COUNT_METRIC_NAME, value, { it.value })
            .baseUnit("count")
            .description("The amount of schemas")
            .tags(tags)
            .register(registry)
    }

    private fun registerAvailibleTablespacesGauge(databaseInstance: DatabaseInstance, value: GaugeValue): Gauge {
        return Gauge.builder(AVAILIBLE_TABLESPACES_METRIC_NAME, value, { it.value })
            .baseUnit("availible")
            .description("The amount of availible tablespaces left on databaseHost")
            .tags(databaseInstance.metricTags)
            .register(registry)
    }

    private fun MutableMap<String, Pair<GaugeValue, Gauge>>.removeDeprecatedMetrics(currentMetricIds: List<String>) {
        val metricsToRemove = keys.minus(currentMetricIds)
        metricsToRemove
            .mapNotNull { this[it] }
            .forEach { (data, gauge) ->
                remove(data.id)
                registry.remove(gauge.id)
            }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ResourceUseCollector::class.java)
        private const val SCHEMA_SIZE_METRIC_NAME = "aurora.dbh.schema.size.bytes"
        private const val SCHEMA_COUNT_METRIC_NAME = "aurora.dbh.schema.count"
        private const val AVAILIBLE_TABLESPACES_METRIC_NAME = "aurora.dbh.availible.tablespaces"
    }
}

private enum class MetricTag(val tagName: String) {
    SCHEMA_ID("id"),
    SCHEMA_TYPE("schemaType"),
    DATABASE_HOST("databaseHost"),
    DATABASE_ENGINE("databaseEngine"),
    AFFILIATION("affiliation"),
    ENVIRONMENT("environment"),
    APPLICATION("application"),
    NAMESPACE("namespace"),
    INSTANCE_NAME("instanceName")
}

private val NAMESPACE_LABEL = ENVIRONMENT.tagName
private val APP_LABEL = APPLICATION.tagName
private val AFFILIATION_LABEL = AFFILIATION.tagName

private val DatabaseSchema.metricTags
    get(): List<Tag> {
        val labels = this.labels
        val t = { tag: MetricTag, value: String? -> tagOfValueOrUnknown(tag.tagName, value) }
        val namespace = labels[NAMESPACE_LABEL]?.removeSuffix("-")
        val affiliation = labels[AFFILIATION_LABEL]
        val environment = affiliation?.let { namespace?.getEnvironment(it) }
        return listOf(
            t(SCHEMA_ID, this.id),
            t(AFFILIATION, affiliation),
            t(APPLICATION, labels[APP_LABEL]),
            t(ENVIRONMENT, environment),
            t(NAMESPACE, namespace),
            t(DATABASE_HOST, this.databaseInstanceMetaInfo.host),
            t(DATABASE_ENGINE, this.databaseInstanceMetaInfo.engine.name),
            t(SCHEMA_TYPE, this.type.name)
        )
    }

private val DatabaseInstance.metricTags
    get(): List<Tag> {
        val t = {tag: MetricTag, value: String? -> tagOfValueOrUnknown(tag.tagName,value)}
        return listOf(
            t(DATABASE_HOST, this.metaInfo.host),
            t(INSTANCE_NAME,  this.instanceName),
            t(DATABASE_ENGINE, this.metaInfo.engine.name)
        )
    }

data class GaugeValue(var id: String, var value: Double)

private fun tagOfValueOrUnknown(name: String, value: String?) = Tag.of(name, value ?: "UNKNOWN")

private typealias CountGroup = Map<String, String?>

private val CountGroup.id get() = this.toSortedMap().map { (k, v) -> "$k=$v" }.joinToString(",")

private fun String.getEnvironment(affiliation: String): String {
    val environment = this.replace("^$affiliation".toRegex(), "").removePrefix("-")
    return if (environment.isBlank()) affiliation else environment
}
