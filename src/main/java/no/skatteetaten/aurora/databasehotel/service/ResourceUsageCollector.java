package no.skatteetaten.aurora.databasehotel.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import no.skatteetaten.aurora.databasehotel.service.oracle.OracleResourceUsageCollector;

public interface ResourceUsageCollector {
    List<OracleResourceUsageCollector.SchemaSize> getSchemaSizes();

    Optional<OracleResourceUsageCollector.SchemaSize> getSchemaSize(String schemaName);

    class SchemaSize {
        private final String owner;

        private final BigDecimal schemaSizeMb;

        public SchemaSize(String owner, BigDecimal schemaSizeMb) {
            this.owner = owner;
            this.schemaSizeMb = schemaSizeMb;
        }

        public String getOwner() {
            return owner;
        }

        public BigDecimal getSchemaSizeMb() {
            return schemaSizeMb;
        }
    }
}
