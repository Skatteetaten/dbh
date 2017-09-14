package no.skatteetaten.aurora.databasehotel.service.oracle;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;

import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseSupport;
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector;

public class OracleResourceUsageCollector extends DatabaseSupport implements ResourceUsageCollector {

    private static final RowMapper<SchemaSize> SCHEMA_SIZE_ROWMAPPER =
        (resultSet, i) -> new SchemaSize(resultSet.getString("owner"), resultSet.getBigDecimal("schema_size_mb"));

    public OracleResourceUsageCollector(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    public List<SchemaSize> getSchemaSizes() {

        return jdbcTemplate.query("SELECT owner, sum(bytes)/1024/1024 schema_size_mb "
            + "FROM dba_segments "
            + "where owner in (select name from DATABASEHOTEL_INSTANCE_DATA.SCHEMA_DATA) "
            + "group BY owner", SCHEMA_SIZE_ROWMAPPER);
    }

    @Override
    public Optional<SchemaSize> getSchemaSize(String schemaName) {

        try {
            return Optional.of(jdbcTemplate.queryForObject("SELECT owner, sum(bytes)/1024/1024 schema_size_mb "
                + "FROM dba_segments "
                + "where owner in (select name from DATABASEHOTEL_INSTANCE_DATA.SCHEMA_DATA where name=?) "
                + "group BY owner", new Object[] { schemaName }, SCHEMA_SIZE_ROWMAPPER));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
