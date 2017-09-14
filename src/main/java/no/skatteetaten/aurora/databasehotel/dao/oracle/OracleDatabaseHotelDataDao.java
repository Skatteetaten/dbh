package no.skatteetaten.aurora.databasehotel.dao.oracle;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import no.skatteetaten.aurora.databasehotel.dao.DataAccessException;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema;
import no.skatteetaten.aurora.databasehotel.dao.dto.Label;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser;

public class OracleDatabaseHotelDataDao extends DatabaseSupport implements DatabaseHotelDataDao {

    public OracleDatabaseHotelDataDao(DataSource dataSource) {

        super(dataSource);
    }

    private static String generateId() {

        return UUID.randomUUID().toString();
    }

    @Override
    public SchemaData createSchemaData(String name) {

        return createSchemaData(name, SCHEMA_TYPE_MANAGED);
    }

    @Override
    public SchemaData createSchemaData(String name, String schemaType) {

        String id = generateId();
        jdbcTemplate.update("insert into SCHEMA_DATA (id, name, schema_type) values (?, ?, ?)", id, name, schemaType);
        return findSchemaDataById(id).orElseThrow(() -> new DataAccessException("Unable to create schema data"));
    }

    @Override
    public Optional<SchemaData> findSchemaDataById(String id) {

        return queryForOne("select id, name, schema_type from SCHEMA_DATA where id=? and active=1", SchemaData.class,
            id);
    }

    @Override
    public Optional<SchemaData> findSchemaDataByName(String name) {

        return queryForOne("select id, name, schema_type from SCHEMA_DATA where name=? and active=1", SchemaData.class,
            name);
    }

    @Override
    public void deleteSchemaData(String id) {

        jdbcTemplate.update("delete from SCHEMA_DATA where id=?", id);
    }

    @Override
    public void deactivateSchemaData(String id) {

        jdbcTemplate.update("update SCHEMA_DATA set active=0 where id=?", id);
    }

    @Override
    public List<SchemaData> findAllManagedSchemaData() {

        return findAllSchemaDataBySchemaType(SCHEMA_TYPE_MANAGED);
    }

    @Override
    public List<SchemaData> findAllSchemaDataBySchemaType(String schemaType) {

        return queryForMany("select id, name, schema_type from SCHEMA_DATA where active=1 and schema_type=?",
            SchemaData.class, schemaType);
    }

    @Override
    public SchemaUser createUser(String schemaId, String userType, String username, String password) {

        Optional<SchemaData> schemaData = findSchemaDataById(schemaId);
        schemaData.orElseThrow(() ->
            new DataAccessException(String.format("Cannot create user for nonexisting schema [%s]", username)));

        String id = generateId();
        jdbcTemplate.update("insert into USERS (id, schema_id, type, username, password) values (?, ?, ?, ?, ?)",
            id, schemaId, userType, username, password);

        return findUserById(id)
            .orElseThrow(() -> new DataAccessException("Expected user to be created but it was not"));
    }

    @Override
    public Optional<SchemaUser> findUserById(String id) {

        return queryForOne("select id, schema_id, type, username, password from USERS where ID=?", SchemaUser.class,
            id);
    }

    @Override
    public List<SchemaUser> findAllUsers() {

        return queryForMany("select id, schema_id, type, username, password from USERS", SchemaUser.class);
    }

    @Override
    public List<SchemaUser> findAllUsersForSchema(String schemaId) {

        return queryForMany("select * from USERS where schema_id=?", SchemaUser.class, schemaId);
    }

    @Override
    public void deleteUsersForSchema(String schemaId) {

        jdbcTemplate.update("delete from USERS where schema_id=?", schemaId);
    }

    @Override
    public void updateUserPassword(String schemaId, String password) {

        String username =
            jdbcTemplate.queryForObject("select name from SCHEMA_DATA where id=?", String.class, schemaId);
        jdbcTemplate
            .update("update USERS set password=? where schema_id=? and username=?", password, schemaId, username);
    }

    @Override
    public List<Label> findAllLabels() {

        return queryForMany("select id, schema_id, name, value from LABELS", Label.class);
    }

    @Override
    public List<Label> findAllLabelsForSchema(String schemaId) {

        return queryForMany("select id, schema_id, name, value from LABELS where schema_id=?", Label.class, schemaId);
    }

    @Override
    public void replaceLabels(String schemaId, Map<String, String> labels) {

        deleteLabelsForSchema(schemaId);
        if (labels == null) {
            return;
        }
        labels.entrySet().forEach(label ->
            jdbcTemplate.update("insert into LABELS (id, schema_id, name, value) values (?, ?, ?, ?)",
                generateId(), schemaId, label.getKey(), label.getValue()));
    }

    @Override
    public void deleteLabelsForSchema(String schemaId) {

        jdbcTemplate.update("delete from LABELS where schema_id=?", schemaId);
    }

    @Override
    public ExternalSchema registerExternalSchema(String schemaId, String jdbcUrl) {

        jdbcTemplate.update("insert into EXTERNAL_SCHEMA (id, created_date, schema_id, jdbc_url) values (?, ?, ?, ?)",
            generateId(), new Date(), schemaId, jdbcUrl);
        return new ExternalSchema(new Date(), jdbcUrl);
    }

    @Override
    public Optional<ExternalSchema> findExternalSchemaById(String id) {

        return queryForOne("select created_date, jdbc_url from EXTERNAL_SCHEMA where schema_id=?",
            ExternalSchema.class, id);
    }

    @Override
    public void deleteExternalSchema(String schemaId) {

        jdbcTemplate.update("delete from EXTERNAL_SCHEMA where schema_id=?", schemaId);
    }

    @Override
    public void updateExternalSchema(String schemaId, String username, String jdbcUrl, String password) {

        if (username != null) {
            jdbcTemplate.update("update SCHEMA_DATA set name=? where id=?", username, schemaId);
            jdbcTemplate.update("update USERS set username=? where schema_id=?", username, schemaId);
        }
        if (jdbcUrl != null) {
            jdbcTemplate.update("update EXTERNAL_SCHEMA set jdbc_url=? where schema_id=?", jdbcUrl, schemaId);
        }
        if (password != null) {
            updateUserPassword(schemaId, password);
        }
    }
}
