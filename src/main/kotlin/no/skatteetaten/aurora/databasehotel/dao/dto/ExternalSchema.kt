package no.skatteetaten.aurora.databasehotel.dao.dto

import java.util.Date

data class ExternalSchema(
    var createdDate: Date? = null,
    var jdbcUrl: String? = null
)
