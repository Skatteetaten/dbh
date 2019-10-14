package no.skatteetaten.aurora.databasehotel.dao

object SchemaDataQueryBuilder {

    enum class COL { ID, ACTIVE, NAME, SCHEMA_TYPE }

    private const val baseQuery =
        "select id, active, name, schema_type, set_to_cooldown_at, delete_after from SCHEMA_DATA"

    fun select(vararg cols: COL) =
        if (cols.isEmpty()) baseQuery
        else "$baseQuery where ${cols.joinToString(separator = " and ") { "$it=?" }}"
}
