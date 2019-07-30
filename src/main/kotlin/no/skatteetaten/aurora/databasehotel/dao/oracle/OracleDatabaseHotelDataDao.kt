package no.skatteetaten.aurora.databasehotel.dao.oracle

import com.google.common.collect.Lists.newArrayList
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes.SCHEMA_TYPE_MANAGED
import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.Collections
import java.util.Date
import java.util.Optional
import java.util.UUID
import javax.sql.DataSource

open class OracleDatabaseHotelDataDao(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseHotelDataDao {

    private fun generateId(): String {

        return UUID.randomUUID().toString()
    }

    override fun createSchemaData(name: String): SchemaData {

        return createSchemaData(name, SCHEMA_TYPE_MANAGED)
    }

    override fun createSchemaData(name: String, schemaType: String): SchemaData {

        val id = generateId()
        jdbcTemplate
            .update("insert into SCHEMA_DATA (id, name, schema_type) values (?, ?, ?)", id, name, schemaType)
        return Optional.ofNullable(findSchemaDataById(id))
            .orElseThrow { DataAccessException("Unable to create schema data") }
    }

    override fun findSchemaDataById(id: String): SchemaData? {

        return queryForOne(
            "select id, name, schema_type from SCHEMA_DATA where id=? and active=1", SchemaData::class.java,
            id
        )
    }

    override fun findSchemaDataByName(name: String): SchemaData? {

        return queryForOne(
            "select id, name, schema_type from SCHEMA_DATA where name=? and active=1", SchemaData::class.java,
            name
        )
    }

    override fun deleteSchemaData(id: String) {

        jdbcTemplate.update("delete from SCHEMA_DATA where id=?", id)
    }

    override fun deactivateSchemaData(id: String) {

        jdbcTemplate.update("update SCHEMA_DATA set active=0 where id=?", id)
    }

    override fun findAllManagedSchemaData(): List<SchemaData> {

        return findAllSchemaDataBySchemaType(SCHEMA_TYPE_MANAGED)
    }

    override fun findAllSchemaDataBySchemaType(schemaType: String): List<SchemaData> {

        return queryForMany(
            "select id, name, schema_type from SCHEMA_DATA where active=1 and schema_type=?",
            SchemaData::class.java, schemaType
        )
    }

    /** Example query:
     * select schema_id
     * from LABELS where name in ('affiliation', 'application', 'environment', 'name')
     * group by schema_id
     * HAVING listagg(value, ',') WITHIN GROUP (ORDER BY name) like 'paas,boober,paas-boober,referanseapp'
     */
    override fun findAllManagedSchemaDataByLabels(labels: Map<String, String?>): List<SchemaData> {

        val namedParameterJdbcTemplate = NamedParameterJdbcTemplate(jdbcTemplate)
        val parameters = MapSqlParameterSource()
        val labelNames = newArrayList(labels.keys)
        Collections.sort(labelNames)
        val labelValues = labelNames.map { labels[it] }.joinToString(",")
        parameters.addValue("names", labelNames)
        parameters.addValue("values", labelValues)
        parameters.addValue("type", SCHEMA_TYPE_MANAGED)

        return namedParameterJdbcTemplate.query(
            """select id, name, schema_type from SCHEMA_DATA where id in (
                select schema_id
                from LABELS where name in (:names)
                group by schema_id
                HAVING listagg(value, ',') WITHIN GROUP (ORDER BY name) like (:values)
                ) and active=1 and schema_type=(:type)""".trimIndent(),
            parameters,
            BeanPropertyRowMapper(SchemaData::class.java)
        )
    }

    override fun createUser(schemaId: String, userType: String, username: String, password: String): SchemaUser {

        Optional.ofNullable(findSchemaDataById(schemaId))
            .orElseThrow {
                DataAccessException(
                    String.format("Cannot create user for nonexisting schema [%s]", username)
                )
            }

        val id = generateId()
        jdbcTemplate.update(
            "insert into USERS (id, schema_id, type, username, password) values (?, ?, ?, ?, ?)",
            id, schemaId, userType, username, password
        )

        return findUserById(id)
            .orElseThrow { DataAccessException("Expected user to be created but it was not") }
    }

    override fun findUserById(id: String): Optional<SchemaUser> {

        return Optional.ofNullable(
            queryForOne(
                "select id, schema_id, type, username, password from USERS where ID=?", SchemaUser::class.java,
                id
            )
        )
    }

    override fun findAllUsers(): List<SchemaUser> {

        return queryForMany("select id, schema_id, type, username, password from USERS", SchemaUser::class.java)
    }

    override fun findAllUsersForSchema(schemaId: String): List<SchemaUser> {

        return queryForMany("select * from USERS where schema_id=?", SchemaUser::class.java, schemaId)
    }

    override fun deleteUsersForSchema(schemaId: String) {

        jdbcTemplate.update("delete from USERS where schema_id=?", schemaId)
    }

    override fun updateUserPassword(schemaId: String, password: String) {

        val username =
            jdbcTemplate.queryForObject("select name from SCHEMA_DATA where id=?", String::class.java, schemaId)
        jdbcTemplate
            .update("update USERS set password=? where schema_id=? and username=?", password, schemaId, username)
    }

    override fun findAllLabels(): List<Label> {

        return queryForMany("select id, schema_id, name, value from LABELS", Label::class.java)
    }

    override fun findAllLabelsForSchema(schemaId: String): List<Label> {

        return queryForMany(
            "select id, schema_id, name, value from LABELS where schema_id=?",
            Label::class.java,
            schemaId
        )
    }

    override fun replaceLabels(schemaId: String, labels: Map<String, String?>) {

        deleteLabelsForSchema(schemaId)
        if (labels == null) {
            return
        }
        labels.entries.forEach { label ->
            jdbcTemplate.update(
                "insert into LABELS (id, schema_id, name, value) values (?, ?, ?, ?)",
                generateId(), schemaId, label.key, label.value
            )
        }
    }

    override fun deleteLabelsForSchema(schemaId: String) {

        jdbcTemplate.update("delete from LABELS where schema_id=?", schemaId)
    }

    override fun registerExternalSchema(schemaId: String, jdbcUrl: String): ExternalSchema {

        jdbcTemplate
            .update(
                "insert into EXTERNAL_SCHEMA (id, created_date, schema_id, jdbc_url) values (?, ?, ?, ?)",
                generateId(), Date(), schemaId, jdbcUrl
            )
        return ExternalSchema(Date(), jdbcUrl)
    }

    override fun findExternalSchemaById(id: String): Optional<ExternalSchema> {

        return Optional.ofNullable(
            queryForOne(
                "select created_date, jdbc_url from EXTERNAL_SCHEMA where schema_id=?",
                ExternalSchema::class.java, id
            )
        )
    }

    override fun deleteExternalSchema(schemaId: String) {

        jdbcTemplate.update("delete from EXTERNAL_SCHEMA where schema_id=?", schemaId)
    }

    override fun updateExternalSchema(schemaId: String, username: String, jdbcUrl: String, password: String) {

        if (username != null) {
            jdbcTemplate.update("update SCHEMA_DATA set name=? where id=?", username, schemaId)
            jdbcTemplate.update("update USERS set username=? where schema_id=?", username, schemaId)
        }
        if (jdbcUrl != null) {
            jdbcTemplate.update("update EXTERNAL_SCHEMA set jdbc_url=? where schema_id=?", jdbcUrl, schemaId)
        }
        if (password != null) {
            updateUserPassword(schemaId, password)
        }
    }
}
