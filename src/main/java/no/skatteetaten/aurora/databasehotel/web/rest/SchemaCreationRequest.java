package no.skatteetaten.aurora.databasehotel.web.rest;

import java.util.Map;

public class SchemaCreationRequest {

    private String instanceName;

    private Map<String, String> labels;

    private Map<String, String> schema;

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public Map<String, String> getSchema() {
        return schema;
    }

    public void setSchema(Map<String, String> schema) {
        this.schema = schema;
    }
}
