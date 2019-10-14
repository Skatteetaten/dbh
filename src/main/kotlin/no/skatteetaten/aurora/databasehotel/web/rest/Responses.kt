package no.skatteetaten.aurora.databasehotel.web.rest

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

object Responses {

    fun okResponse(resource: Any): ResponseEntity<ApiResponse<*>> = okResponse(listOf(resource))

    @JvmOverloads
    fun okResponse(resources: List<*> = emptyList<Any>()): ResponseEntity<ApiResponse<*>> =
        ResponseEntity(ApiResponse(resources), HttpStatus.OK)
}
