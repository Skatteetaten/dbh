package no.skatteetaten.aurora.databasehotel.dao.oracle

import com.google.common.collect.Lists.newArrayList
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.sql.DataSource
import no.skatteetaten.aurora.databasehotel.dao.DataAccessException
import no.skatteetaten.aurora.databasehotel.dao.DatabaseHotelDataDao
import no.skatteetaten.aurora.databasehotel.dao.DatabaseSupport
import no.skatteetaten.aurora.databasehotel.dao.SchemaTypes.SCHEMA_TYPE_MANAGED
import no.skatteetaten.aurora.databasehotel.dao.dto.ExternalSchema
import no.skatteetaten.aurora.databasehotel.dao.dto.Label
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaData
import no.skatteetaten.aurora.databasehotel.dao.dto.SchemaUser
import no.skatteetaten.aurora.databasehotel.dao.oracle.SchemaDataQueryBuilder.COL
import no.skatteetaten.aurora.databasehotel.dao.oracle.SchemaDataQueryBuilder.COL.ACTIVE
import no.skatteetaten.aurora.databasehotel.dao.oracle.SchemaDataQueryBuilder.COL.ID
import no.skatteetaten.aurora.databasehotel.dao.oracle.SchemaDataQueryBuilder.COL.NAME
import no.skatteetaten.aurora.databasehotel.dao.oracle.SchemaDataQueryBuilder.COL.SCHEMA_TYPE
import no.skatteetaten.aurora.databasehotel.dao.oracle.SchemaDataQueryBuilder.select
import org.springframework.jdbc.core.BeanPropertyRowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional

object SchemaDataQueryBuilder {

    enum class COL { ID, ACTIVE, NAME, SCHEMA_TYPE }

    private const val baseQuery =
        "select id, active, name, schema_type, set_to_cooldown_at, delete_after from SCHEMA_DATA"

    fun select(vararg cols: COL) =
        if (cols.isEmpty()) baseQuery
        else "$baseQuery where ${cols.joinToString(separator = " and ") { "$it=?" }}"
}

fun Boolean.toInt(): Int = if (this) 1 else 0

open class OracleDatabaseHotelDataDao(dataSource: DataSource) : DatabaseSupport(dataSource), DatabaseHotelDataDao {
    private fun generateId(): String {

        return UUID.randomUUID().toString()
    }

    override fun createSchemaData(name: String): SchemaData = createSchemaData(name, SCHEMA_TYPE_MANAGED)

    override fun createSchemaData(name: String, schemaType: String): SchemaData {

        val id = generateId()
        jdbcTemplate.update(
            "insert into SCHEMA_DATA (id, name, schema_type, active) values (?, ?, ?, ?)", id, name, schemaType, 1
        )
        return findSchemaDataById(id) ?: throw DataAccessException("Unable to create schema data")
    }

    override fun findSchemaDataById(id: String, active: Boolean) =
        selectOneSchemaData(ID to id, ACTIVE to active.toInt())

    override fun findSchemaDataByName(name: String) = selectOneSchemaData(NAME to name, ACTIVE to 1)

    override fun findSchemaDataByNameIgnoreActive(name: String) = selectOneSchemaData(NAME to name)

    override fun deleteSchemaData(id: String) {
        jdbcTemplate.update("delete from SCHEMA_DATA where id=?", id)
    }

    @Transactional(readOnly = false)
    override fun deactivateSchemaData(id: String, cooldownDuration: Duration) {

        val ts = Timestamp::from
        val now = Instant.now()
        val deleteAfter = now.plus(cooldownDuration)
        jdbcTemplate.update(
            "update SCHEMA_DATA set active=0, set_to_cooldown_at=?, delete_after=? where id=?",
            ts(now), ts(deleteAfter), id
        )
    }

    override fun findAllManagedSchemaData(): List<SchemaData> = findAllSchemaDataBySchemaType(SCHEMA_TYPE_MANAGED)

    override fun findAllManagedSchemaDataIgnoreActive() = selectManySchemaData(SCHEMA_TYPE to SCHEMA_TYPE_MANAGED)

    override fun findAllSchemaDataBySchemaType(schemaType: String): List<SchemaData> =
        selectManySchemaData(SCHEMA_TYPE to schemaType, ACTIVE to 1)

    override fun findAllManagedSchemaDataByDeleteAfterDate(deleteAfter: Date): List<SchemaData> = queryForMany(
        "${select()} where schema_type=? and delete_after<?", SchemaData::class.java, SCHEMA_TYPE_MANAGED, deleteAfter
    )

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
        labelNames.sort()
        val labelValues = labelNames.map { labels[it] }.joinToString(",")
        parameters.addValue("names", labelNames)
        parameters.addValue("values", labelValues)
        parameters.addValue("type", SCHEMA_TYPE_MANAGED)

        return namedParameterJdbcTemplate.query(
            """${select()} where id in (
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

        findSchemaDataById(schemaId)
            ?: throw DataAccessException("Cannot create user for nonexisting schema [$username]")

        val id = generateId()
        jdbcTemplate.update(
            "insert into USERS (id, schema_id, type, username, password) values (?, ?, ?, ?, ?)",
            id, schemaId, userType, username, password
        )

        return findUserById(id) ?: throw DataAccessException("Expected user to be created but it was not")
    }

    override fun findUserById(id: String): SchemaUser? = queryForOne(
        "select id, schema_id, type, username, password from USERS where ID=?", SchemaUser::class.java, id
    )

    override fun findAllUsers(): List<SchemaUser> =
        queryForMany("select id, schema_id, type, username, password from USERS", SchemaUser::class.java)

    override fun findAllUsersForSchema(schemaId: String): List<SchemaUser> =
        queryForMany("select * from USERS where schema_id=?", SchemaUser::class.java, schemaId)

    override fun deleteUsersForSchema(schemaId: String) {

        jdbcTemplate.update("delete from USERS where schema_id=?", schemaId)
    }

    override fun updateUserPassword(schemaId: String, password: String) {

        val username = findSchemaDataById(schemaId)?.name ?: throw DataAccessException("No such schema id=$schemaId")
        jdbcTemplate.update(
            "update USERS set password=? where schema_id=? and username=?", password, schemaId, username
        )
    }

    override fun findAllLabels(): List<Label> =
        queryForMany("select id, schema_id, name, value from LABELS", Label::class.java)

    override fun findAllLabelsForSchema(schemaId: String): List<Label> =
        queryForMany("select id, schema_id, name, value from LABELS where schema_id=?", Label::class.java, schemaId)

    override fun replaceLabels(schemaId: String, labels: Map<String, String?>) {

        deleteLabelsForSchema(schemaId)
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

    override fun findExternalSchemaById(id: String): ExternalSchema? = queryForOne(
        "select created_date, jdbc_url from EXTERNAL_SCHEMA where schema_id=?",
        ExternalSchema::class.java,
        id
    )

    override fun deleteExternalSchema(schemaId: String) {

        jdbcTemplate.update("delete from EXTERNAL_SCHEMA where schema_id=?", schemaId)
    }

    override fun updateExternalSchema(schemaId: String, username: String?, jdbcUrl: String?, password: String?) {

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

    private fun selectOneSchemaData(vararg args: Pair<COL, Any>): SchemaData? {
        val cols = args.map { it.first }.toTypedArray()
        val values = args.map { it.second }.toTypedArray()
        return queryForOne(select(*cols), SchemaData::class.java, *values)
    }

    private fun selectManySchemaData(vararg args: Pair<COL, Any>): List<SchemaData> {
        val cols = args.map { it.first }.toTypedArray()
        val values = args.map { it.second }.toTypedArray()
        return queryForMany(select(*cols), SchemaData::class.java, *values)
    }
}
