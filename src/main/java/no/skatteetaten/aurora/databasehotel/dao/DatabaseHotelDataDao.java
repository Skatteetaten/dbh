package no.skatteetaten.aurora.databasehotel.dao;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema;
import no.skatteetaten.aurora.databasehotel.dao.dto.Label;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData;
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser;

public interface DatabaseHotelDataDao {

    String SCHEMA_TYPE_MANAGED = "MANAGED";

    String SCHEMA_TYPE_EXTERNAL = "EXTERNAL";

    SchemaData createSchemaData(String name);

    SchemaData createSchemaData(String name, String schemaType);

    Optional<SchemaData> findSchemaDataById(String id);

    Optional<SchemaData> findSchemaDataByName(String name);

    void deleteSchemaData(String id);

    void deactivateSchemaData(String id);

    List<SchemaData> findAllManagedSchemaData();

    List<SchemaData> findAllSchemaDataBySchemaType(String schemaType);

    List<SchemaData> findAllManagedSchemaDataByLabels(Map<String, String> labels);

    SchemaUser createUser(String schemaId, String userType, String username, String password);

    Optional<SchemaUser> findUserById(String id);

    List<SchemaUser> findAllUsers();

    List<SchemaUser> findAllUsersForSchema(String schemaId);

    void deleteUsersForSchema(String schemaId);

    void updateUserPassword(String schemaId, String password);

    List<Label> findAllLabels();

    List<Label> findAllLabelsForSchema(String schemaId);

    void replaceLabels(String schemaId, Map<String, String> labels);

    void deleteLabelsForSchema(String schemaId);

    ExternalSchema registerExternalSchema(String id, String jdbcUrl);

    Optional<ExternalSchema> findExternalSchemaById(String id);

    void deleteExternalSchema(String schemaId);

    void updateExternalSchema(String schemaId, String username, String jdbcUrl, String password);
}
