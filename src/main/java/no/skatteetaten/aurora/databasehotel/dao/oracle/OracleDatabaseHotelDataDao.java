package no.skatteetaten.aurora.databasehotel.dao.oracle;

import static com.google.common.collect.Lists.newArrayList;

import static no.skatteetaten.aurora.databasehotel.dao.SchemaTypes.SCHEMA_TYPE_MANAGED;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import no.skatteetaten.aurora.databasehotel.dao.DataAccessException;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao;
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport;
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
        getJdbcTemplate()
            .update("insert into SCHEMA_DATA (id, name, schema_type) values (?, ?, ?)", id, name, schemaType);
        return Optional.ofNullable(findSchemaDataById(id))
            .orElseThrow(() -> new DataAccessException("Unable to create schema data"));
    }

    @Override
    public SchemaData findSchemaDataById(String id) {

        return queryForOne("select id, name, schema_type from SCHEMA_DATA where id=? and active=1", SchemaData.class,
            id);
    }

    @Override
    public SchemaData findSchemaDataByName(String name) {

        return queryForOne("select id, name, schema_type from SCHEMA_DATA where name=? and active=1", SchemaData.class,
            name);
    }

    @Override
    public void deleteSchemaData(String id) {

        getJdbcTemplate().update("delete from SCHEMA_DATA where id=?", id);
    }

    @Override
    public void deactivateSchemaData(String id) {

        getJdbcTemplate().update("update SCHEMA_DATA set active=0 where id=?", id);
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
    /** Example query:
     * select schema_id
     * from LABELS where name in ('affiliation', 'application', 'environment', 'name')
     * group by schema_id
     * HAVING listagg(value, ',') WITHIN GROUP (ORDER BY name) like 'paas,boober,paas-boober,referanseapp'
     */
    public List<SchemaData> findAllManagedSchemaDataByLabels(Map<String, String> labels) {

        NamedParameterJdbcTemplate namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(getJdbcTemplate());
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        List<String> labelNames = newArrayList(labels.keySet());
        Collections.sort(labelNames);
        String labelValues = labelNames.stream()
            .map(it -> labels.get(it))
            .collect(Collectors.joining(","));
        parameters.addValue("names", labelNames);
        parameters.addValue("values", labelValues);
        parameters.addValue("type", SCHEMA_TYPE_MANAGED);

        return namedParameterJdbcTemplate.query(
            "select id, name, schema_type from SCHEMA_DATA where id in (\n"
                + "select schema_id\n"
                + "from LABELS where name in (:names)\n"
                + "group by schema_id\n"
                + "HAVING listagg(value, ',') WITHIN GROUP (ORDER BY name) like (:values)\n"
                + ") and active=1 and schema_type=(:type)",
            parameters,
            new BeanPropertyRowMapper<>(SchemaData.class)
        );
    }

    @Override
    public SchemaUser createUser(String schemaId, String userType, String username, String password) {

        Optional.ofNullable(findSchemaDataById(schemaId))
            .orElseThrow(() -> new DataAccessException(
                String.format("Cannot create user for nonexisting schema [%s]", username)));

        String id = generateId();
        getJdbcTemplate().update("insert into USERS (id, schema_id, type, username, password) values (?, ?, ?, ?, ?)",
            id, schemaId, userType, username, password);

        return findUserById(id)
            .orElseThrow(() -> new DataAccessException("Expected user to be created but it was not"));
    }

    @Override
    public Optional<SchemaUser> findUserById(String id) {

        return Optional.ofNullable(
            queryForOne("select id, schema_id, type, username, password from USERS where ID=?", SchemaUser.class,
                id));
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

        getJdbcTemplate().update("delete from USERS where schema_id=?", schemaId);
    }

    @Override
    public void updateUserPassword(String schemaId, String password) {

        String username =
            getJdbcTemplate().queryForObject("select name from SCHEMA_DATA where id=?", String.class, schemaId);
        getJdbcTemplate()
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
            getJdbcTemplate().update("insert into LABELS (id, schema_id, name, value) values (?, ?, ?, ?)",
                generateId(), schemaId, label.getKey(), label.getValue()));
    }

    @Override
    public void deleteLabelsForSchema(String schemaId) {

        getJdbcTemplate().update("delete from LABELS where schema_id=?", schemaId);
    }

    @Override
    public ExternalSchema registerExternalSchema(String schemaId, String jdbcUrl) {

        getJdbcTemplate()
            .update("insert into EXTERNAL_SCHEMA (id, created_date, schema_id, jdbc_url) values (?, ?, ?, ?)",
                generateId(), new Date(), schemaId, jdbcUrl);
        return new ExternalSchema(new Date(), jdbcUrl);
    }

    @Override
    public Optional<ExternalSchema> findExternalSchemaById(String id) {

        return Optional.ofNullable(queryForOne("select created_date, jdbc_url from EXTERNAL_SCHEMA where schema_id=?",
            ExternalSchema.class, id));
    }

    @Override
    public void deleteExternalSchema(String schemaId) {

        getJdbcTemplate().update("delete from EXTERNAL_SCHEMA where schema_id=?", schemaId);
    }

    @Override
    public void updateExternalSchema(String schemaId, String username, String jdbcUrl, String password) {

        if (username != null) {
            getJdbcTemplate().update("update SCHEMA_DATA set name=? where id=?", username, schemaId);
            getJdbcTemplate().update("update USERS set username=? where schema_id=?", username, schemaId);
        }
        if (jdbcUrl != null) {
            getJdbcTemplate().update("update EXTERNAL_SCHEMA set jdbc_url=? where schema_id=?", jdbcUrl, schemaId);
        }
        if (password != null) {
            updateUserPassword(schemaId, password);
        }
    }
}
