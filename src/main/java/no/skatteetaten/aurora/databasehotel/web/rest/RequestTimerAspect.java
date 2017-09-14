package no.skatteetaten.aurora.databasehotel.web.rest;

import static java.lang.String.format;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.common.base.Strings;

/**
 * A simple aspect that will log the execution time of the controller methods, the url of the requrest and the
 * response code.
 * <p>
 * In spring-boot earlier than 1.4.0 when the Get-, Post-, Put-, and DeleteMappings were not available, and when we
 * always used RequestMapping we did not need around advice around every type of mapping. No big deal.
 */
@Component
@Aspect
public class RequestTimerAspect {

    private static Logger log = LoggerFactory.getLogger(RequestTimerAspect.class);

    @Around("execution(public * no.skatteetaten.aurora.databasehotel.web.rest.*.*(..)) && @annotation(requestMapping)")
    Object handleGets(ProceedingJoinPoint pjp, GetMapping requestMapping) throws Throwable {

        return handleMethod(pjp);
    }

    @Around("execution(public * no.skatteetaten.aurora.databasehotel.web.rest.*.*(..)) && @annotation(requestMapping)")
    Object handlePosts(ProceedingJoinPoint pjp, PostMapping requestMapping) throws Throwable {

        return handleMethod(pjp);
    }

    @Around("execution(public * no.skatteetaten.aurora.databasehotel.web.rest.*.*(..)) && @annotation(requestMapping)")
    Object handlePuts(ProceedingJoinPoint pjp, PutMapping requestMapping) throws Throwable {

        return handleMethod(pjp);
    }

    @Around("execution(public * no.skatteetaten.aurora.databasehotel.web.rest.*.*(..)) && @annotation(requestMapping)")
    Object handleDeletes(ProceedingJoinPoint pjp, DeleteMapping requestMapping) throws Throwable {

        return handleMethod(pjp);
    }

    Object handleMethod(ProceedingJoinPoint pjp) throws Throwable {
        ServletRequestAttributes sra = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();

        HttpServletRequest request = sra.getRequest();
        HttpServletResponse response = sra.getResponse();

        String requestUrl = request.getRequestURL().toString();

        String queryString = !Strings.isNullOrEmpty(request.getQueryString()) ? "?" + request.getQueryString() : "";

        long s = System.currentTimeMillis();
        Object object = pjp.proceed();
        long ts = System.currentTimeMillis() - s;

        log.info(format("[%4d ms] %6s (%d) / %s%s", ts, request.getMethod(), response.getStatus(), requestUrl,
            queryString));

        return object;
    }
}
