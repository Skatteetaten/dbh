package no.skatteetaten.aurora.databasehotel.service;

import static no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao.SCHEMA_TYPE_MANAGED;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager;
import no.skatteetaten.aurora.databasehotel.dao.dto.Label;
import no.skatteetaten.aurora.databasehotel.dao.Schema;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.service.internal.DatabaseSchemaBuilder;
import no.skatteetaten.aurora.databasehotel.service.oracle.OracleResourceUsageCollector;
import no.skatteetaten.aurora.databasehotel.utils.Assert;

public class DatabaseInstance {

    public static final int DAYS_BACK = -7;

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseInstance.class);

    private final DatabaseInstanceMetaInfo metaInfo;

    private final DatabaseManager databaseManager;

    private final DatabaseHotelDataDao databaseHotelDataDao;

    private final JdbcUrlBuilder jdbcUrlBuilder;

    private final ResourceUsageCollector resourceUsageCollector;

    private List<Integration> integrations = new ArrayList<>();

    private SchemaNamePasswordStrategy schemaNamePasswordStrategy = SchemaNamePasswordStrategyKt::createSchemaNameAndPassword;

    private final boolean createSchemaAllowed;

    private final int cooldownDaysAfterDelete;

    private final int cooldownDaysForOldUnusedSchemas;

    public DatabaseInstance(DatabaseInstanceMetaInfo metaInfo, DatabaseManager databaseManager,
        DatabaseHotelDataDao databaseHotelDataDao,
        JdbcUrlBuilder jdbcUrlBuilder, ResourceUsageCollector resourceUsageCollector,
        int cooldownDaysAfterDelete, int cooldownDaysForOldUnusedSchemas) {

        this.metaInfo = metaInfo;
        this.databaseManager = databaseManager;
        this.databaseHotelDataDao = databaseHotelDataDao;
        this.jdbcUrlBuilder = jdbcUrlBuilder;
        this.resourceUsageCollector =
            Assert.asNotNull(resourceUsageCollector, "%s must be set", ResourceUsageCollector.class);
        this.createSchemaAllowed = metaInfo.getCreateSchemaAllowed();
        this.cooldownDaysAfterDelete = cooldownDaysAfterDelete;
        this.cooldownDaysForOldUnusedSchemas = cooldownDaysForOldUnusedSchemas;
    }

    public DatabaseInstanceMetaInfo getMetaInfo() {

        return metaInfo;
    }

    public Optional<DatabaseSchema> findSchemaById(String id) {

        return databaseHotelDataDao.findSchemaDataById(id).map(this::getDatabaseSchemaFromSchemaData);
    }

    public Optional<DatabaseSchema> findSchemaByName(String name) {

        return databaseHotelDataDao.findSchemaDataByName(name).map(this::getDatabaseSchemaFromSchemaData);
    }

    public Set<DatabaseSchema> findAllSchemas(Map<String, String> labelsToMatch) {

        if (labelsToMatch == null || labelsToMatch.isEmpty()) {
            return findAllSchemasUnfiltered();
        } else {
            return findAllSchemasWithLabels(labelsToMatch);
        }
    }

    private Set<DatabaseSchema> findAllSchemasUnfiltered() {

        List<SchemaData> schemaData = databaseHotelDataDao.findAllManagedSchemaData();
        List<SchemaUser> users = databaseHotelDataDao.findAllUsers();
        List<Schema> schemas = databaseManager.findAllNonSystemSchemas();
        List<Label> labels = databaseHotelDataDao.findAllLabels();
        List<OracleResourceUsageCollector.SchemaSize> schemaSizes = resourceUsageCollector.getSchemaSizes();

        return new DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder)
            .createMany(schemaData, schemas, users, labels, schemaSizes);
    }

    private Set<DatabaseSchema> findAllSchemasWithLabels(Map<String, String> labelsToMatch) {

        List<SchemaData> schemaData = databaseHotelDataDao.findAllManagedSchemaDataByLabels(labelsToMatch);

        List<SchemaUser> users = schemaData.stream()
            .flatMap((it) -> databaseHotelDataDao.findAllUsersForSchema(it.getId())
                .stream()).collect(Collectors.toList());

        List<Schema> schemas = schemaData.stream()
            .map((it) -> databaseManager.findSchemaByName(it.getName()))
            .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
            .collect(Collectors.toList());

        List<Label> labels = schemaData.stream()
            .flatMap((it) -> databaseHotelDataDao.findAllLabelsForSchema(it.getId())
                .stream()).collect(Collectors.toList());

        List<OracleResourceUsageCollector.SchemaSize> schemaSizes = resourceUsageCollector.getSchemaSizes();

        return new DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder)
            .createMany(schemaData, schemas, users, labels, schemaSizes);
    }

    @Transactional
    public DatabaseSchema createSchema(Map<String, String> labelsOption) {

        Pair<String, String> schemaNameAndPassword = createSchemaNameAndPassword();
        String schemaName = schemaNameAndPassword.getLeft();
        String password = schemaNameAndPassword.getRight();

        return createSchema(schemaName, password, labelsOption);
    }

    @Transactional
    public DatabaseSchema createSchema(String schemaName, Map<String, String> labelsOption) {

        Pair<String, String> schemaNameAndPassword = createSchemaNameAndPassword();
        String password = schemaNameAndPassword.getRight();

        return createSchema(schemaName, password, labelsOption);
    }

    @Transactional
    public DatabaseSchema createSchema(String schemaName, String password, Map<String, String> labelsOption) {

        if (!createSchemaAllowed) {
            throw new DatabaseServiceException(String.format("Schema creation has been disabled for this instance=%s",
                getInstanceName()));
        }

        String schemaNameValid = databaseManager.createSchema(schemaName, password);
        SchemaData schemaData = databaseHotelDataDao.createSchemaData(schemaNameValid);
        databaseHotelDataDao.createUser(schemaData.getId(), UserType.SCHEMA.toString(), schemaData.getName(), password);

        DatabaseSchema databaseSchema = findSchemaByName(schemaNameValid).orElseThrow(() ->
            new DatabaseServiceException(
                String.format("Expected schema [%s] to be created, but it was not", schemaNameValid)));

        Optional.ofNullable(labelsOption).ifPresent(labels -> replaceLabels(databaseSchema, labels));

        integrations.forEach(integration -> integration.onSchemaCreated(databaseSchema));

        return databaseSchema;
    }

    @Transactional
    public void deleteSchema(String schemaName, DeleteParams deleteParams) {

        Optional<DatabaseSchema> optionalSchema = findSchemaByName(schemaName);
        if (deleteParams.isAssertExists()) {
            optionalSchema
                .orElseThrow(() -> new DatabaseServiceException(String.format("No schema named [%s]", schemaName)));
        }

        optionalSchema.ifPresent(databaseSchema -> {
            databaseHotelDataDao.findSchemaDataByName(schemaName).ifPresent(schemaData -> {
                Date lastUsedDate = databaseSchema.getLastUsedDate();
                String lastUsedString = lastUsedDate != null ? lastUsedDate.toInstant().toString() : "Never";
                LOGGER.info("Deleting schema id={}, lastUsed={}, size(mb)={}, name={}, labels={}. Setting cooldown={}h",
                    schemaData.getId(), lastUsedString, databaseSchema.getSizeMb(), schemaData.getName(),
                    databaseSchema.getLabels(), deleteParams.getCooldownDuration().toHours());
                databaseHotelDataDao.deactivateSchemaData(schemaData.getId());
                // We need to make sure that users can no longer connect to the schema. Let's just create a new random
                // password for the schema so that it is different from the one we have in the SchemaData.
                databaseManager.updatePassword(schemaName, createSchemaNameAndPassword().getRight());
            });
            integrations.forEach(
                integration -> integration.onSchemaDeleted(databaseSchema, deleteParams.getCooldownDuration()));
        });
    }

    @Transactional
    public void deleteSchema(String schemaName, @Nullable Duration cooldownDuration) {

        if (cooldownDuration == null) {
            cooldownDuration = Duration.ofDays(cooldownDaysAfterDelete);
        }

        deleteSchema(schemaName, new DeleteParams(cooldownDuration));
    }

    @Transactional
    public void deleteUnusedSchemas() {

        LOGGER.info("Deleting schemas old unused schemas for server {}", metaInfo.getInstanceName());

        Set<DatabaseSchema> schemas = findAllSchemasForDeletion();
        LOGGER.info("Found {} old and unused schemas", schemas.size());
        schemas.parallelStream().forEach(schema -> {
            deleteSchema(schema.getName(), Duration.ofDays(cooldownDaysForOldUnusedSchemas));
        });
    }

    public Set<DatabaseSchema> findAllSchemasForDeletion() {

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, DAYS_BACK);
        Date daysAgo = c.getTime();

        return findAllSchemas(null).stream()
            .filter(it -> {
                Map<String, String> labels = it.getLabels() != null ? it.getLabels() : new HashMap<>();
                boolean isSystemTest = labels.getOrDefault("userId", "").endsWith(":jenkins-builder");
                return it.isUnused() || isSystemTest;
            })
            .filter(s -> s.getLastUsedOrCreatedDate().before(daysAgo))
            .collect(Collectors.toSet());
    }

    @Transactional
    public void replaceLabels(DatabaseSchema schema, Map<String, String> labels) {

        schema.setLabels(labels);
        databaseHotelDataDao.replaceLabels(schema.getId(), schema.getLabels());
        integrations.forEach((integration -> integration.onSchemaUpdated(schema)));
    }

    public String getInstanceName() {

        return metaInfo.getInstanceName();
    }

    public boolean isCreateSchemaAllowed() {

        return createSchemaAllowed;
    }

    public void registerIntegration(Integration integration) {

        this.integrations.add(integration);
    }

    public DatabaseHotelDataDao getDatabaseHotelDataDao() {

        return databaseHotelDataDao;
    }

    private Pair<String, String> createSchemaNameAndPassword() {

        return schemaNamePasswordStrategy.createSchemaNameAndPassword();
    }

    private DatabaseSchema getDatabaseSchemaFromSchemaData(SchemaData schemaData) {

        if (!SCHEMA_TYPE_MANAGED.equals(schemaData.getSchemaType())) {
            return null;
        }
        return databaseManager.findSchemaByName(schemaData.getName()).map(schema -> {
            List<SchemaUser> users = databaseHotelDataDao.findAllUsersForSchema(schemaData.getId());
            List<Label> labels = databaseHotelDataDao.findAllLabelsForSchema(schemaData.getId());

            Optional<ResourceUsageCollector.SchemaSize> schemaSize =
                resourceUsageCollector.getSchemaSize(schemaData.getName());

            return new DatabaseSchemaBuilder(metaInfo, jdbcUrlBuilder).createOne(schemaData, schema, users,
                Optional.ofNullable(labels), schemaSize);
        }).orElse(null);
    }

    public enum UserType {
        SCHEMA,
        READONLY,
        READWRITE
    }
}
