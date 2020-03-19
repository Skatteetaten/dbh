@file:JvmName("JdbcUtils")

package no.skatteetaten.aurora.databasehotel.dao

import java.sql.ResultSet
import java.util.Date
import org.springframework.jdbc.core.RowMapper

var toSchema: RowMapper<Schema> = RowMapper { rs, _ ->
    Schema(
        rs.getString("username"),
        rs.dateOrNull("created"),
        rs.getTimestamp("lastLogin")?.let { Date(it.time) })
}

fun ResultSet.date(columnName: String): Date? = dateOrNull(columnName)

private fun ResultSet.dateOrNull(columnName: String): Date? {
    return if (this.getObject(columnName) == null) {
        null
    } else
        Date(getTimestamp(columnName).time)
}

// TODO både oracle og postgres bruker toSchema i jdbcutils.kt.