package no.skatteetaten.aurora.databasehotel.web.rest

class ApiResponse<T> @JvmOverloads constructor(
    val items: List<T>,
    val status: String = "OK",
    val totalCount: Int = items.size
)