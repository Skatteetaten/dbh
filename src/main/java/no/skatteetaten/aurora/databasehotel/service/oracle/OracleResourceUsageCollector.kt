package no.skatteetaten.aurora.databasehotel.service.oracle

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector
import no.skatteetaten.aurora.databasehotel.service.SchemaSize
import java.sql.ResultSet
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

class OracleResourceUsageCollector(dataSource: DataSource, resourceUseCollectInterval: Long) :
    DatabaseSupport(dataSource), ResourceUsageCollector {

    val cache: LoadingCache<Any, List<SchemaSize>> = Caffeine.newBuilder()
        .expireAfterWrite(resourceUseCollectInterval, TimeUnit.MILLISECONDS)
        .build { jdbcTemplate.query(QUERY, MAPPER) }

    override val schemaSizes: List<SchemaSize>
        get() = cache.get("_")!! // This will actually never be null. The cache will always return the same value regardless of key.

    override fun getSchemaSize(schemaName: String) = schemaSizes.firstOrNull { it.owner == schemaName }

    fun invalidateCache() {
        cache.invalidateAll()
    }

    companion object {
        private const val QUERY = "SELECT owner, sum(bytes)/1024/1024 schema_size_mb FROM dba_segments group BY owner"

        private val MAPPER = { rs: ResultSet, _: Int ->
            SchemaSize(rs.getString("owner"), rs.getBigDecimal("schema_size_mb"))
        }
    }
}
