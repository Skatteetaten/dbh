package no.skatteetaten.aurora.databasehotel.web.rest;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler({ OperationDisabledException.class })
    protected ResponseEntity<Object> handleOperationDisabledException(OperationDisabledException e,
        WebRequest request) {

        return handleException(e, request, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({ Throwable.class })
    protected ResponseEntity<Object> handleGenericError(Exception e, WebRequest request) {

        LOGGER.error("Unexpected error", e);
        return handleException(e, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    protected ResponseEntity<Object> handleIllegalArgument(RuntimeException e, WebRequest request) {

        LOGGER.warn("IllegalArgument", e);
        if (request instanceof ServletWebRequest) {
            ServletWebRequest servletWebRequest = (ServletWebRequest) request;
            if (servletWebRequest.getHttpMethod().equals(HttpMethod.GET)) {
                return handleException(e, request, HttpStatus.NOT_FOUND);
            }
        }
        return handleException(e, request, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Object> handleException(final Exception e, WebRequest request,
        HttpStatus httpStatus) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> error = new HashMap<String, Object>() {
            {
                put("errorMessage", e.getMessage());
                if (e.getCause() != null) {
                    put("cause", e.getCause().getMessage());
                }
            }
        };
        return handleExceptionInternal(e, error, headers, httpStatus, request);
    }
}
