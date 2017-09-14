package no.skatteetaten.aurora.databasehotel.service.internal;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Verify;

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;

public class SchemaLabelMatcher {

    public static Set<DatabaseSchema> findAllMatchingSchemas(Set<DatabaseSchema> schemas, Map<String, String> labels) {

        Verify.verifyNotNull(schemas, "Schemas cannot be null");
        Verify.verifyNotNull(labels, "Labels cannot be null");

        return schemas.stream().filter(schema -> matchesAll(schema, labels)).collect(Collectors.toSet());
    }

    public static boolean matchesAll(DatabaseSchema schema, Map<String, String> labels) {

        Verify.verifyNotNull(schema, "Schema cannot be null");
        Verify.verifyNotNull(labels, "Labels cannot be null");

        boolean matches = true;
        Map<String, String> schemaLabels = schema.getLabels();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            String valueToMatch = label.getValue();
            String schemaLabelValue = schemaLabels.get(label.getKey());
            if (!StringUtils.equals(valueToMatch, schemaLabelValue)) {
                matches = false;
                break;
            }
        }
        return matches;
    }
}
