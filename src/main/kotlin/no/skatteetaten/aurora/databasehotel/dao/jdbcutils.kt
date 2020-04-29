@file:JvmName("JdbcUtils")

package no.skatteetaten.aurora.databasehotel.dao

import org.springframework.jdbc.core.RowMapper

var toSchema: RowMapper<Schema> = RowMapper { rs, _ ->
    rs.run { Schema(getString("username"), getTimestamp("lastLogin")) }
}