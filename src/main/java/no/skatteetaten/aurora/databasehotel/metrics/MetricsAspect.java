package no.skatteetaten.aurora.databasehotel.metrics;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class MetricsAspect {

    @Autowired
    private CounterService counterService;

    @Around("execution(public * no.skatteetaten.aurora.databasehotel.service.DatabaseHotelService.createSchema(..))")
    public Object schemaCreated(ProceedingJoinPoint pjp) throws Throwable {

        Object proceed = pjp.proceed();
        counterService.increment("no.skatteetaten.aurora.databasehotel.schemas.created");
        return proceed;
    }
}
