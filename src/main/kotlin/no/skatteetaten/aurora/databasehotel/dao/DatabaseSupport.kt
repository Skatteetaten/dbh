package no.skatteetaten.aurora.databasehotel.dao

import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import javax.sql.DataSource

abstract class DatabaseSupport(dataSource: DataSource) {

    protected val jdbcTemplate: JdbcTemplate = JdbcTemplate(dataSource)

    fun <T> queryForMany(query: String, dtoType: Class<T>, vararg params: Any): List<T> =
        jdbcTemplate.query(query, BeanPropertyRowMapper(dtoType), *params)

    fun <T> queryForOne(query: String, dtoType: Class<T>, vararg params: Any): Optional<T> {

        val objects = queryForMany(query, dtoType, *params)
        return if (objects.isEmpty()) Optional.empty() else Optional.of(objects[0])
    }

    fun executeStatements(vararg statements: String) {

        statements.forEach(jdbcTemplate::execute)
    }
}
