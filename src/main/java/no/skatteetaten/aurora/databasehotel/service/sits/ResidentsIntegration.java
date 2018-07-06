package no.skatteetaten.aurora.databasehotel.service.sits;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.Validate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.Assert;

import no.skatteetaten.aurora.databasehotel.domain.DatabaseSchema;
import no.skatteetaten.aurora.databasehotel.service.Integration;

public class ResidentsIntegration implements Integration {

    private final JdbcTemplate jdbcTemplate;

    public ResidentsIntegration(DataSource dataSource) {

        Validate.notNull(dataSource, "DataSource cannot be null");
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    private static String createServiceNameFromLabels(Map<String, String> labels) {
        String affiliation = getRequiredLabel(labels, "affiliation");
        String environment = getRequiredLabel(labels, "environment");
        String application = getRequiredLabel(labels, "application");
        String name = getRequiredLabel(labels, "name");
        return String.format("%s/%s/%s/%s", affiliation, environment, application, name);
    }

    private static String getRequiredLabel(Map<String, String> labels, String labelName) {
        String userId = labels.get(labelName);
        Assert.hasText(userId, String.format("%s label is missing", labelName));
        return userId;
    }

    @Override
    public void onSchemaCreated(DatabaseSchema databaseSchema) {

        onSchemaUpdated(databaseSchema);
    }

    @Override
    public void onSchemaDeleted(DatabaseSchema databaseSchema, Duration cooldownDuration) {

        Validate.notNull(databaseSchema, "DatabaseSchema cannot be null");
        Validate.notNull(cooldownDuration, "cooldownDuration cannot be null");

        String schemaName = databaseSchema.getName();

        if (!residentsEntryForSchemaExists(schemaName)) {
            createResidentsEntryForSchema(schemaName);
        }

        LocalDateTime deleteAfterDate = LocalDateTime.now().plus(cooldownDuration);
        Date out = Date.from(deleteAfterDate.atZone(ZoneId.systemDefault()).toInstant());
        setResidentsEntryRemoveAfter(schemaName, out);
    }

    @Override
    public void onSchemaUpdated(DatabaseSchema databaseSchema) {

        Validate.notNull(databaseSchema, "DatabaseSchema cannot be null");

        String schemaName = databaseSchema.getName();

        if (!residentsEntryForSchemaExists(schemaName)) {
            createResidentsEntryForSchema(schemaName);
        }
        updateResidentsEntryForSchemaWithLabelData(schemaName, databaseSchema.getLabels());
    }

    private boolean residentsEntryForSchemaExists(String schemaName) {
        Integer residentCount = jdbcTemplate.queryForObject("select count(*) from RESIDENTS.RESIDENTS "
            + "where RESIDENT_NAME=?", Integer.class, schemaName);
        return residentCount > 0;
    }

    private void createResidentsEntryForSchema(String schemaName) {

        jdbcTemplate.update("insert into RESIDENTS.RESIDENTS (RESIDENT_NAME, RESIDENT_EMAIL, RESIDENT_SERVICE) "
            + "values (?, 'ukjent', 'ukjent')", schemaName);
    }

    private void updateResidentsEntryForSchemaWithLabelData(String schemaName, Map<String, String> labels) {

        String userId = getRequiredLabel(labels, "userId");
        String service = createServiceNameFromLabels(labels);
        jdbcTemplate.update("update RESIDENTS.RESIDENTS set RESIDENT_EMAIL=?, RESIDENT_SERVICE=? "
            + "where RESIDENT_NAME=?", userId, service, schemaName);
    }

    private void setResidentsEntryRemoveAfter(String schemaName, Date time) {
        jdbcTemplate.update("update RESIDENTS.RESIDENTS set RESIDENT_REMOVE_AFTER=? "
            + "where RESIDENT_NAME=?", time, schemaName);
    }
}
