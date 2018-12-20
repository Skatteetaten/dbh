package no.skatteetaten.aurora.databasehotel.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService;
import no.skatteetaten.aurora.databasehotel.utils.MapUtils;

@Component
public class ResourceUseCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceUseCollector.class);
    private static final String METRIC_NAME = "aurora.dbh.schema.size.mb";
    private static final String ENVIRONMENT_LABEL = "environment";
    private static final String APP_LABEL = "application";
    private static final String AFFILIATION_LABEL = "affiliation";
    private static final String ENVIRONMENT_METRIC_LABEL = "namespace";
    private final MeterRegistry registry;
    private final DatabaseHotelService databaseHotelService;
    private final Map<String, SchemaSizeValue> schemaSizeValues = new HashMap<>();

    public ResourceUseCollector(DatabaseHotelService databaseHotelService, MeterRegistry registry) {
        this.registry = registry;
        this.databaseHotelService = databaseHotelService;
    }

    @Scheduled(fixedDelayString = "${metrics.resourceUseCollectInterval}", initialDelay = 15000)
    public void collectResourceUseMetrics() {

        long start = System.currentTimeMillis();
        LOG.info("Collecting resource use metrics");

        Set<DatabaseSchema> allSchemas = databaseHotelService.findAllDatabaseSchemas(null);
        LOG.debug("Found {} schemas total", allSchemas.size());

        List<DatabaseSchema> databaseSchemas = allSchemas.stream()
            .filter(s -> MapUtils.containsEveryKey(s.getLabels(), ENVIRONMENT_LABEL, APP_LABEL, AFFILIATION_LABEL))
            .collect(Collectors.toList());

        // Let's update the SchemaSizeValues that backs the schema size gauges
        databaseSchemas.forEach(schema -> {
            SchemaSizeValue existingValue = schemaSizeValues.get(schema.getId());
            if (existingValue != null) {
                existingValue.setSizeMb(schema.getSizeMb());
            } else {
                SchemaSizeValue value = new SchemaSizeValue(schema.getId(), schema.getSizeMb());
                schemaSizeValues.put(schema.getId(), value);
                registerGaugeForSchema(schema, value);
            }
        });

        // Remove the SchemaSizeValues that does not currently have a DatabaseSchema object. This indicates that
        // the DatabaseSchema has been deleted since the last time we updated the gauges. Deleting the value that
        // backs a Gauge *should* also in fact delete the Gauge since the reference to the SchemaSizeValue is a
        // WeakReference. This does not appear to happen (bug?(!)), and to make matters worse, deleting the Gauge
        // from the MeterRegistry does not delete it from the io.prometheus.client.CollectorRegistry instance that is
        // used to render the data for Prometheus. I'm hoping and assuming that these issues will be fixed in a future
        // version of micrometer.io, so I'm leaving the implementation as is - even if that means leaving deleted
        // DatabaseSchemas with a NaN size.
        schemaSizeValues.keySet().removeIf(
            schemaId -> {
                boolean isAnExistingSchema =
                    databaseSchemas.stream().anyMatch(databaseSchema -> schemaId.equals(databaseSchema.getId()));
                boolean isSchemaRemovedSinceLastIteration = !isAnExistingSchema;
                return isSchemaRemovedSinceLastIteration;
            });

        LOG.info("Resource use metrics collected for {} schemas in {} millis", databaseSchemas.size(),
            System.currentTimeMillis() - start);
    }

    private void registerGaugeForSchema(DatabaseSchema schema, SchemaSizeValue value) {

        Map<String, String> labels = schema.getLabels();
        Gauge.builder(METRIC_NAME, value, SchemaSizeValue::getSizeMb)
            .description("the size of the individual registered schemas")
            .tags(createLabelsArray(labels))
            .register(registry);
    }

    private String[] createLabelsArray(Map<String, String> labels) {
        return new String[] { AFFILIATION_LABEL, labels.get(AFFILIATION_LABEL),
            APP_LABEL, labels.get(APP_LABEL),
            ENVIRONMENT_METRIC_LABEL, labels.get(ENVIRONMENT_LABEL) };
    }

    public static class SchemaSizeValue {
        private String id;

        private double sizeMb;

        public SchemaSizeValue(String id, double sizeMb) {
            this.id = id;
            this.sizeMb = sizeMb;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public double getSizeMb() {
            return sizeMb;
        }

        public void setSizeMb(double sizeMb) {
            this.sizeMb = sizeMb;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SchemaSizeValue that = (SchemaSizeValue) o;

            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}
