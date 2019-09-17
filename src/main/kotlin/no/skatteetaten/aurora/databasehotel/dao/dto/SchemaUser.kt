package no.skatteetaten.aurora.databasehotel.dao.dto

data class SchemaUser(
    var id: String? = null,
    var schemaId: String? = null,
    var type: String? = null,
    var username: String? = null,
    var password: String? = null
)
