package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.dao.DatabaseManager

fun DatabaseManager.deleteNonSystemSchemas() {
    this.apply { findAllNonSystemSchemas().forEach { deleteSchema(it.username) } }
}