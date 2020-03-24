package no.skatteetaten.aurora.databasehotel.dao.dto

import java.util.Date

data class SchemaData(
    var id: String = "",
    var active: Boolean = true,
    var name: String = "",
    var schemaType: String? = null,
    var setToCooldownAt: Date? = null,
    var deleteAfter: Date? = null,
    var createdDate: Date = Date()
)
