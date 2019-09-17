package no.skatteetaten.aurora.databasehotel.service.internal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import no.skatteetaten.aurora.databasehotel.dao.Schema;
import no.skatteetaten.aurora.databasehotel.dao.dto.Label;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseInstanceMetaInfo;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchemaMetaData;
import no.skatteetaten.aurora.databasehotel.domain.User;
import no.skatteetaten.aurora.databasehotel.service.JdbcUrlBuilder;
import no.skatteetaten.aurora.databasehotel.service.SchemaSize;
import no.skatteetaten.aurora.databasehotel.utils.MapUtils;

public class DatabaseSchemaBuilder {

    private final DatabaseInstanceMetaInfo metaInfo;

    private final JdbcUrlBuilder jdbcUrlBuilder;

    public DatabaseSchemaBuilder(DatabaseInstanceMetaInfo metaInfo, JdbcUrlBuilder jdbcUrlBuilder) {

        this.metaInfo = metaInfo;
        this.jdbcUrlBuilder = jdbcUrlBuilder;
    }

    public Set<DatabaseSchema> createMany(
        List<SchemaData> schemaDataList,
        List<Schema> schemas,
        List<SchemaUser> users,
        List<Label> labels,
        List<SchemaSize> schemaSizes) {

        Map<String, SchemaData> schemaDataIndex = MapUtils.createUniqueIndex(schemaDataList, SchemaData::getName);
        Map<String, SchemaUser> userIndex = MapUtils.createUniqueIndex(users, SchemaUser::getSchemaId);
        Map<String, List<Label>> labelIndex = MapUtils.createNonUniqueIndex(labels, Label::getSchemaId);
        Map<String, SchemaSize> schemaSizeIndex = MapUtils.createUniqueIndex(schemaSizes, SchemaSize::getOwner);
        return schemas.stream()
            .filter(schema -> schemaDataIndex.containsKey(schema.getUsername()))
            .map(schema -> {
                SchemaData schemaData = schemaDataIndex.get(schema.getUsername());

                Optional<SchemaUser> user = MapUtils.maybeGet(userIndex, schemaData.getId());
                List<SchemaUser> schemaUsers = new ArrayList<>();
                user.ifPresent(schemaUsers::add);

                Optional<List<Label>> schemaLabels = MapUtils.maybeGet(labelIndex, schemaData.getId());
                SchemaSize schemaSize = schemaSizeIndex.get(schema.getUsername());

                return createOne(schemaData, schema, schemaUsers, schemaLabels, schemaSize);
            })
            .collect(Collectors.toSet());
    }

    public DatabaseSchema createOne(SchemaData schemaData, Schema schema, List<SchemaUser> users,
        Optional<List<Label>> labelsOption, SchemaSize schemaSize) {

        return createOne(schemaData, schema, users, labelsOption, schemaSize, DatabaseSchema.Type.MANAGED);
    }

    public DatabaseSchema createOne(SchemaData schemaData, Schema schema, List<SchemaUser> users,
        List<Label> labelsOption, SchemaSize schemaSize, DatabaseSchema.Type type) {
        return createOne(schemaData, schema, users, Optional.ofNullable(labelsOption), schemaSize, type);
    }

    public DatabaseSchema createOne(SchemaData schemaData, Schema schema, List<SchemaUser> users,
        Optional<List<Label>> labelsOption, SchemaSize schemaSize, DatabaseSchema.Type type) {

        String jdbcUrl = jdbcUrlBuilder.create(metaInfo.getHost(), metaInfo.getPort(), schema.getUsername());

        DatabaseSchema databaseSchema = new DatabaseSchema(
            schemaData.getId(),
            metaInfo,
            jdbcUrl,
            schema.getUsername(),
            schema.getCreated(),
            schema.getLastLogin(),
            createMetaData(schemaSize),
            type
        );
        users.forEach(u -> databaseSchema.addUser(new User(u.getId(), u.getUsername(), u.getPassword(), u.getType())));

        Map<String, String> labelMap = new HashMap<>();
        labelsOption.ifPresent(labels -> labels.forEach(label -> labelMap.put(label.getName(), label.getValue())));
        databaseSchema.setLabels(labelMap);

        return databaseSchema;
    }

    private DatabaseSchemaMetaData createMetaData(SchemaSize schemaSize) {

        double size = Optional.ofNullable(schemaSize)
            .map(SchemaSize::getSchemaSizeMb)
            .map(BigDecimal::doubleValue)
            .orElse(0.0);
        return new DatabaseSchemaMetaData(size);
    }
}
