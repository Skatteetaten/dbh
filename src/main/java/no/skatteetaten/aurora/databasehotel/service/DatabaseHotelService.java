package no.skatteetaten.aurora.databasehotel.service;

import static java.lang.String.format;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.service.internal.SchemaLabelMatcher;

@Service
public class DatabaseHotelService {

    private static Logger log = LoggerFactory.getLogger(DatabaseHotelService.class);

    private final DatabaseHotelAdminService databaseHotelAdminService;

    @Autowired
    public DatabaseHotelService(DatabaseHotelAdminService databaseHotelAdminService) {

        this.databaseHotelAdminService = databaseHotelAdminService;
    }

    public Optional<Pair<DatabaseSchema, DatabaseInstance>> findSchemaById(String id) {

        List<Pair<DatabaseSchema, DatabaseInstance>> candidates = Lists.newArrayList();

        Set<DatabaseInstance> allDatabaseInstances = databaseHotelAdminService.findAllDatabaseInstances();
        for (DatabaseInstance databaseInstance : allDatabaseInstances) {
            Optional<Pair<DatabaseSchema, DatabaseInstance>> schemaAndInstance = databaseInstance.findSchemaById(id)
                .map(dbs -> Pair.of(dbs, databaseInstance));
            schemaAndInstance.ifPresent(candidates::add);
        }

        databaseHotelAdminService.getExternalSchemaManager()
            .map(externalSchemaManager -> externalSchemaManager.findSchemaById(id).orElse(null))
            .map(databaseSchema -> Pair.of(databaseSchema, (DatabaseInstance) null))
            .ifPresent(candidates::add);

        if (candidates.size() > 1) {
            throw new IllegalStateException(String
                .format("More than one schema from different database servers matched the specified id [%s]", id));
        }
        return candidates.size() == 0 ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public Set<DatabaseSchema> findAllDatabaseSchemas() {

        return findAllDatabaseSchemasByLabels(null);
    }

    public Set<DatabaseSchema> findAllDatabaseSchemasByLabels(Map<String, String> labelsToMatch) {

        Set<DatabaseSchema> schemas = new HashSet<>();
        Set<DatabaseInstance> databaseInstances = databaseHotelAdminService.findAllDatabaseInstances();
        for (DatabaseInstance databaseInstance : databaseInstances) {

            Set<DatabaseSchema> databaseSchemas = databaseInstance.findAllSchemas(labelsToMatch);
            schemas.addAll(databaseSchemas);
        }
        databaseHotelAdminService.getExternalSchemaManager()
            .ifPresent(externalSchemaManager -> {
                Set<DatabaseSchema> externalSchemas = externalSchemaManager.findAllSchemas();
                schemas.addAll(SchemaLabelMatcher.findAllMatchingSchemas(externalSchemas, labelsToMatch));
            });
        return schemas;
    }

    public DatabaseSchema createSchema() {

        return createSchema(null);
    }

    public DatabaseSchema createSchema(String instanceName) {

        return createSchema(instanceName, null);
    }

    public DatabaseSchema createSchema(String instanceNameOption, Map<String, String> labels) {

        DatabaseInstance databaseInstance = databaseHotelAdminService.findDatabaseInstanceOrFail(instanceNameOption);
        DatabaseSchema schema = databaseInstance.createSchema(labels);

        log.info("Created schema name={}, id={} with labels={}", schema.getName(), schema.getId(),
            schema.getLabels() != null ? schema.getLabels().toString() : null);
        return schema;
    }

    public void deleteSchemaById(String id) {

        findSchemaById(id).ifPresent(schemaAndInstance -> {
            DatabaseSchema schema = schemaAndInstance.getLeft();
            DatabaseInstance databaseInstance = schemaAndInstance.getRight();
            if (databaseInstance != null) {
                databaseInstance.deleteSchema(schema.getName());
            } else {
                // If the schema was not found on a database instance, it is an external schema
                databaseHotelAdminService.getExternalSchemaManager()
                    .ifPresent(externalSchemaManager -> externalSchemaManager.deleteSchema(id));
            }
        });
    }

    public void deleteSchema(String instanceName, String schema) {

        DatabaseInstance databaseInstance = databaseHotelAdminService.findDatabaseInstanceByHost(instanceName)
            .orElseThrow(() -> new DatabaseServiceException(format("No instance named [%s]", instanceName)));
        databaseInstance.deleteSchema(schema);
    }

    public DatabaseSchema updateSchema(String id, Map<String, String> labels) {

        return updateSchema(id, labels, null, null, null);
    }

    public DatabaseSchema updateSchema(String id, Map<String, String> labels, String username, String jdbcUrl,
        String password) {

        log.info("Updating labels for schema with id={} to labels={}", id, labels);

        Pair<DatabaseSchema, DatabaseInstance> schemaAndInstance =
            findSchemaById(id).orElseThrow(() -> new DatabaseServiceException(format("No such schema %s", id)));

        DatabaseInstance databaseInstance = schemaAndInstance.getRight();
        if (databaseInstance != null) {
            DatabaseSchema schema = schemaAndInstance.getLeft();
            databaseInstance.replaceLabels(schema, labels);
            return schema;
        } else {
            DatabaseSchema schema = schemaAndInstance.getLeft();
            Optional<DatabaseSchema> databaseSchema = databaseHotelAdminService.getExternalSchemaManager()
                .map(externalSchemaManager -> {
                    externalSchemaManager.replaceLabels(schema, labels);
                    externalSchemaManager.updateConnectionInfo(schema.getId(), username, jdbcUrl, password);
                    return externalSchemaManager.findSchemaById(id).orElse(null);
                });
            return databaseSchema.orElse(null);
        }
    }

    public DatabaseSchema registerExternalSchema(String username, String password, String jdbcUrl,
        Map<String, String> labels) {

        return databaseHotelAdminService.getExternalSchemaManager().map(
            externalSchemaManager -> externalSchemaManager.registerSchema(username, password, jdbcUrl, labels))
            .orElseThrow(() -> new DatabaseServiceException("External Schema Manager has not been registered"));
    }
}
