package no.skatteetaten.aurora.databasehotel.service;

import org.apache.commons.lang3.tuple.Pair;

public interface SchemaNamePasswordStrategy {
    Pair<String, String> createSchemaNameAndPassword();
}
