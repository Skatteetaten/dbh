package no.skatteetaten.aurora.databasehotel.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ResourceUsageCollector {

    List<SchemaSize> getSchemaSizes();

    Optional<SchemaSize> getSchemaSize(String schemaName);

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
