package no.skatteetaten.aurora.databasehotel.web.rest

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

private val logger = KotlinLogging.logger {}

@Component
class LogRequestsInterceptor : HandlerInterceptorAdapter() {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute("startTime", System.currentTimeMillis())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        super.afterCompletion(request, response, handler, ex)
        val startTime = request.getAttribute("startTime") as Long
        val elapsedTime = System.currentTimeMillis() - startTime
        val queryParam = if (request.queryString.isNullOrEmpty()) "" else "?${request.queryString}"

        logger.info { "[$elapsedTime ms] ${request.method} ${response.status} / ${request.requestURL}$queryParam" }
    }
}
