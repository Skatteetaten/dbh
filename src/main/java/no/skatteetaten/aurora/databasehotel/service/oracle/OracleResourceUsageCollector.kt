package no.skatteetaten.aurora.databasehotel.service.oracle

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.service.ResourceUsageCollector
import no.skatteetaten.aurora.databasehotel.service.SchemaSize
import java.sql.ResultSet
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

private class Loader<K, V>(val function: (K) -> V) : CacheLoader<K, V>() {
    override fun load(key: K): V? = function(key)
}

private val rowMapper = { rs: ResultSet, _: Int ->
    SchemaSize(rs.getString("owner"), rs.getBigDecimal("schema_size_mb"))
}

class OracleResourceUsageCollector(dataSource: DataSource, resourceUseCollectInterval: Long) :
    DatabaseSupport(dataSource), ResourceUsageCollector {

    private val cache = CacheBuilder.newBuilder()
        .expireAfterWrite(resourceUseCollectInterval, TimeUnit.MILLISECONDS)
        .build(Loader<Any, List<SchemaSize>> {
            jdbcTemplate.query(
                "SELECT owner, sum(bytes)/1024/1024 schema_size_mb FROM dba_segments group BY owner",
                rowMapper
            )
        })

    override val schemaSizes: List<SchemaSize>
        get() = cache.get("_")

    override fun getSchemaSize(schemaName: String) = schemaSizes.firstOrNull { it.owner == schemaName }

    fun invalidateCache() {
        cache.invalidateAll()
    }
}
