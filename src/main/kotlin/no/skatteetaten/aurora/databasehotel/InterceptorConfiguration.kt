package no.skatteetaten.aurora.databasehotel

import no.skatteetaten.aurora.databasehotel.web.rest.LogRequestsInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class InterceptorConfiguration : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(LogRequestsInterceptor())
            // FIXME: what path patterns should not be included?
            .excludePathPatterns("/docs/**")
    }
}
