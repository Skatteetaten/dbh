package no.skatteetaten.aurora.databasehotel.dao.postgres

import javax.sql.DataSource
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.oracle.OracleDatabaseHotelDataDao
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

class PostgresDatabaseHotelDataDao(dataSource: DataSource) : OracleDatabaseHotelDataDao(dataSource) {
    override fun findAllManagedSchemaDataByLabels(labels: Map<String, String?>): MutableList<SchemaData> {

        val labelNames = labels.keys.toList().sorted()
        val labelValues = labelNames.joinToString(",") { labels[it]!! }

        val parameters = MapSqlParameterSource().apply {
            addValue("names", labelNames)
            addValue("values", labelValues)
            addValue("type", SchemaTypes.SCHEMA_TYPE_MANAGED)
        }

        return NamedParameterJdbcTemplate(jdbcTemplate).query(
            """select id, name, schema_type from SCHEMA_DATA where id in (
                    select schema_id
                    from labels
                    where name in (:names)
                    group by schema_id
                    having string_agg(value, ',' order by name) like (:values)
                ) and active=1 and schema_type=(:type)
                """.trimIndent(),
            parameters,
            BeanPropertyRowMapper(SchemaData::class.java)
        )
    }
}
