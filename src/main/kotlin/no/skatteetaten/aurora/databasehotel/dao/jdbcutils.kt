@file:JvmName("JdbcUtils")

package no.skatteetaten.aurora.databasehotel.dao

import java.sql.ResultSet
import java.util.Date
import org.springframework.jdbc.core.RowMapper

var toSchema: RowMapper<Schema> = RowMapper { rs, _ ->
    Schema(
        rs.getString("username"),
        rs.date("created"),
        rs.getTimestamp("lastLogin")?.let { Date(it.time) })
}

fun ResultSet.date(columnName: String) = Date(this.getTimestamp(columnName).time)
