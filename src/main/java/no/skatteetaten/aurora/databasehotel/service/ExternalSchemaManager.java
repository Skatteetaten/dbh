package no.skatteetaten.aurora.databasehotel.service;

import static java.lang.String.format;

import static no.skatteetaten.aurora.databasehotel.DatabaseEngine.ORACLE;
import static no.skatteetaten.aurora.databasehotel.dao.SchemaTypes.SCHEMA_TYPE_EXTERNAL;
import static no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema.Type.EXTERNAL;
import static no.skatteetaten.aurora.databasehotel.service.DatabaseInstance.UserType.SCHEMA;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.Schema;
import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema;
import no.skatteetaten.aurora.databasehotel.dao.dto.Label;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.service.internal.DatabaseSchemaBuilder;

public class ExternalSchemaManager {

    private DatabaseHotelDataDao databaseHotelDataDao;

    public ExternalSchemaManager(DatabaseHotelDataDao databaseHotelDataDao) {

        this.databaseHotelDataDao = databaseHotelDataDao;
    }

    public Optional<DatabaseSchema> findSchemaById(String schemaId) {

        Optional<SchemaData> maybeSchemaData = Optional.ofNullable(databaseHotelDataDao.findSchemaDataById(schemaId))
            .filter(schemaData -> SCHEMA_TYPE_EXTERNAL.equals(schemaData.getSchemaType()));
        return maybeSchemaData.map(schemaData -> {
            ExternalSchema externalSchema = databaseHotelDataDao.findExternalSchemaById(schemaId)
                .orElseThrow(() -> new DatabaseServiceException(format("Could not find ExternalSchema data for "
                    + "schema with id %s", schemaId)));
            List<SchemaUser> users = databaseHotelDataDao.findAllUsersForSchema(schemaId);
            return createDatabaseSchema(schemaData, externalSchema, users);
        });
    }

    public Set<DatabaseSchema> findAllSchemas() {

        List<SchemaData> externalSchemas = databaseHotelDataDao.findAllSchemaDataBySchemaType(SCHEMA_TYPE_EXTERNAL);
        // TODO: Iterating like this may (will) become a performance bottleneck at some point.
        return externalSchemas.stream().map(schemaData -> {
            String id = schemaData.getId();
            ExternalSchema externalSchema = databaseHotelDataDao.findExternalSchemaById(id)
                .orElseThrow(() -> new DatabaseServiceException(format("Could not find ExternalSchema data for "
                    + "schema with id %s", id)));
            List<SchemaUser> users = databaseHotelDataDao.findAllUsersForSchema(id);
            return createDatabaseSchema(schemaData, externalSchema, users);
        }).collect(Collectors.toSet());
    }

    public DatabaseSchema registerSchema(String username, String password, String jdbcUrl,
        Map<String, String> labelMap) {

        SchemaData schemaData =
            databaseHotelDataDao.createSchemaData(username, SCHEMA_TYPE_EXTERNAL);
        ExternalSchema externalSchema = databaseHotelDataDao.registerExternalSchema(schemaData.getId(), jdbcUrl);
        databaseHotelDataDao.replaceLabels(schemaData.getId(), labelMap);
        SchemaUser user = databaseHotelDataDao.createUser(schemaData.getId(), SCHEMA.toString(), username, password);
        List<SchemaUser> users = Lists.newArrayList(user);

        return createDatabaseSchema(schemaData, externalSchema, users);
    }

    public void deleteSchema(String schemaId) {

        databaseHotelDataDao.deleteSchemaData(schemaId);
        databaseHotelDataDao.deleteUsersForSchema(schemaId);
        databaseHotelDataDao.deleteLabelsForSchema(schemaId);
        databaseHotelDataDao.deleteExternalSchema(schemaId);
    }

    private DatabaseSchema createDatabaseSchema(SchemaData schemaData, ExternalSchema externalSchema,
        List<SchemaUser> users) {

        Schema schema = new Schema(schemaData.getName(), externalSchema.getCreatedDate());

        List<Label> labels = databaseHotelDataDao.findAllLabelsForSchema(schemaData.getId());

        DatabaseInstanceMetaInfo metaInfo =
            new DatabaseInstanceMetaInfo(ORACLE, "external", "-", 0, false, new HashMap<>());
        JdbcUrlBuilder jdbcUrlBuilder = (host, port, database) -> externalSchema.getJdbcUrl();

        return new DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder).createOne(schemaData, schema, users,
            Optional.ofNullable(labels),
            Optional.of(new ResourceUsageCollector.SchemaSize(schemaData.getName(), BigDecimal.ZERO)),
            EXTERNAL);
    }

    public void replaceLabels(DatabaseSchema schema, Map<String, String> labels) {

        schema.setLabels(labels);
        databaseHotelDataDao.replaceLabels(schema.getId(), labels);
    }

    public void updateConnectionInfo(String schemaId, String username, String jdbcUrl, String password) {

        databaseHotelDataDao.updateExternalSchema(schemaId, username, jdbcUrl, password);
    }
}
