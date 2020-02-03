package no.skatteetaten.aurora.databasehotel.dao.dto

import java.util.Date

data class ExternalSchemaFull(
    var id: String = "",
    var active: Boolean = true,
    var name: String = "",
    var schemaType: String? = null,
    var setToCooldownAt: Date? = null,
    var deleteAfter: Date? = null,

    var createdDate: Date? = null,
    var jdbcUrl: String? = null,

    var userId: String? = null,
    var type: String? = null,
    var username: String? = null,
    var password: String? = null,

    var labels: List<Label> = emptyList()
)
