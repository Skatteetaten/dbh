package no.skatteetaten.aurora.databasehotel.service.oracle;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.jdbc.core.RowMapper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import no.skatteetaten.aurora.databasehotel.dao.oracle.DatabaseSupport;
import no.skatteetaten.aurora.databasehotel.service.DatabaseServiceException;
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector;

public class OracleResourceUsageCollector extends DatabaseSupport implements ResourceUsageCollector {

    private static final RowMapper<SchemaSize> SCHEMA_SIZE_ROWMAPPER =
        (resultSet, i) -> new SchemaSize(resultSet.getString("owner"), resultSet.getBigDecimal("schema_size_mb"));

    private static final String KEYS_ARE_NOT_USED = "ANY";

    final private LoadingCache<Object, List<SchemaSize>> cache;

    public OracleResourceUsageCollector(DataSource dataSource, Long resourceUseCollectInterval) {
        super(dataSource);

        cache = CacheBuilder.newBuilder().expireAfterWrite(resourceUseCollectInterval, TimeUnit.MILLISECONDS)
            .build(new CacheLoader<Object, List<SchemaSize>>() {
                @Override
                public List<SchemaSize> load(Object key) throws Exception {
                    List<SchemaSize> schemaSizes =
                        jdbcTemplate.query("SELECT owner, sum(bytes)/1024/1024 schema_size_mb "
                            + "FROM dba_segments "
                            + "where owner in (select name from DATABASEHOTEL_INSTANCE_DATA.SCHEMA_DATA) "
                            + "group BY owner", SCHEMA_SIZE_ROWMAPPER);
                    return schemaSizes;
                }
            });
    }

    @Override
    public List<SchemaSize> getSchemaSizes() {

        try {
            return cache.get(KEYS_ARE_NOT_USED);
        } catch (ExecutionException e) {
            throw new DatabaseServiceException("An error occurred while getting schema sizes", e);
        }
    }

    @Override
    public Optional<SchemaSize> getSchemaSize(String schemaName) {

        return getSchemaSizes().stream().filter(schemaSize -> schemaSize.getOwner().equals(schemaName)).findFirst();
    }

    public void invalidateCache() {

        cache.invalidateAll();
    }
}
