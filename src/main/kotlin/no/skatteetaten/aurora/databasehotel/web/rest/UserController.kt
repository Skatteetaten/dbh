package no.skatteetaten.aurora.databasehotel.web.rest

import no.skatteetaten.aurora.databasehotel.domain.User

data class UserResource(val username: String, val password: String, val type: String)

fun User.toResource() = UserResource(name, password, type)