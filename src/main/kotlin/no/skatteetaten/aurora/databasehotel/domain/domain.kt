package no.skatteetaten.aurora.databasehotel.domain

import java.util.Date
import java.util.HashMap
import java.util.HashSet
import no.skatteetaten.aurora.databasehotel.DatabaseEngine

data class DatabaseInstanceMetaInfo(
    val engine: DatabaseEngine,
    val instanceName: String,
    val host: String,
    val port: Int,
    val createSchemaAllowed: Boolean,
    val labels: Map<String, String>
)

data class DatabaseSchemaMetaData(val sizeInMb: Double)

data class User(
    val id: String,
    val name: String,
    val password: String,
    val type: String
)

data class DatabaseSchema @JvmOverloads constructor(
    val id: String,
    val active: Boolean,
    val databaseInstanceMetaInfo: DatabaseInstanceMetaInfo,
    val jdbcUrl: String,
    val name: String,
    val createdDate: Date?,
    val lastUsedDate: Date?,
    val setToCooldownAt: Date?,
    val deleteAfter: Date?,
    val metadata: DatabaseSchemaMetaData?,
    val type: Type = Type.MANAGED
) {
    private val _users = HashSet<User>()
    private val _labels = HashMap<String, String?>()

    val lastUsedOrCreatedDate: Date? get() = lastUsedDate ?: createdDate

    val isUnused: Boolean get() = lastUsedDate == null

    val sizeMb: Double get() = metadata?.sizeInMb ?: 0.0

    fun addUser(user: User) {

        this._users.removeIf { (_, name1) -> name1 == user.name }
        this._users.add(user)
    }

    val users get() = HashSet(_users)

    var labels
        get(): Map<String, String?> = HashMap<String, String?>().apply { putAll(_labels) }
        set(labels) {
            this._labels.clear()
            this._labels.putAll(labels)
        }

    enum class Type {
        MANAGED,
        EXTERNAL
    }
}
