package no.skatteetaten.aurora.databasehotel.service

import org.apache.commons.lang3.RandomStringUtils

private const val RANDOM_LENGTH = 30

fun createSchemaNameAndPassword(): Pair<String, String> {
    // To prevent passwords starting with a number (which is surprisingly not allowed in some databases), and
    // guaranteeing that the password contains at least a letter and a number (which is required by some databases).
    val padding = "a1"
    val schemaName = RandomStringUtils.randomAlphabetic(RANDOM_LENGTH)
    val password = padding + RandomStringUtils.randomAlphanumeric(RANDOM_LENGTH - padding.length)
    return Pair(schemaName, password)
}
