package no.skatteetaten.aurora.databasehotel.dao

import javax.sql.DataSource
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.JdbcTemplate

abstract class DatabaseSupport(dataSource: DataSource) {

    val jdbcTemplate = JdbcTemplate(dataSource)

    val dataSource = jdbcTemplate.dataSource!!

    fun <T> queryForMany(query: String, dtoType: Class<T>, vararg params: Any): List<T> =
        jdbcTemplate.query(query, BeanPropertyRowMapper(dtoType), *params)

    fun <T> queryForOne(query: String, dtoType: Class<T>, vararg params: Any): T? =
        queryForMany(query, dtoType, *params).firstOrNull()

    fun executeStatements(vararg statements: String) {
        statements.forEach(jdbcTemplate::execute)
    }
}
