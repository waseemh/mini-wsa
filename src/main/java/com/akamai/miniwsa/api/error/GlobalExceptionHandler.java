package com.akamai.miniwsa.api.error;

import com.akamai.miniwsa.api.error.ApiError.FieldError;
import com.akamai.miniwsa.ingest.InvalidEventsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.UUID;

/**
 * Turns every failure into an {@link ApiError}. Spring's own MVC exceptions (404, 405, 415, ...) keep their
 * status and headers but get our body; anything unexpected becomes a 500 that leaks nothing.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(InvalidEventsException.class)
    ResponseEntity<ApiError> invalidEvents(InvalidEventsException ex) {
        return ResponseEntity.badRequest().body(new ApiError(400, ErrorCode.VALIDATION_FAILED,
                "One or more events are invalid", null, ex.errors(), ex.truncated() ? true : null));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<Object> invalidRequest(InvalidRequestException ex) {
        return badRequest(new FieldError(null, ex.field(), ex.code(), ex.getMessage()));
    }

    /** The database is unreachable or timed out: the client can retry, so say 503 rather than 500. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class,
            CannotCreateTransactionException.class})
    ResponseEntity<ApiError> storageUnavailable(Exception ex) {
        String errorId = newErrorId();
        log.warn("Storage unavailable [errorId={}]: {}", errorId, ex.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(new ApiError(503, ErrorCode.SERVICE_UNAVAILABLE,
                        "Storage is temporarily unavailable; retry shortly", errorId, null, null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        String errorId = newErrorId();
        log.error("Unexpected error [errorId={}]", errorId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, ErrorCode.INTERNAL_ERROR,
                        "Unexpected error; quote the errorId when reporting it", errorId, null, null));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or is not valid JSON"));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return badRequest(FieldErrors.required(null, ex.getParameterName()));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return badRequest(FieldErrors.wrongType(null, name, ex.getRequiredType()));
    }

    /** Everything else Spring MVC can throw (404, 405, 415, ...): keep its status and headers, use our body. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).headers(headers)
                .body(ApiError.of(status.value(), ErrorCode.ofStatus(status.value()), defaultMessage(status)));
    }

    private static String defaultMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "No such endpoint";
            case 405 -> "Method not allowed for this endpoint";
            case 406 -> "Cannot respond in the requested format; use application/json";
            case 415 -> "Unsupported content type; send application/json";
            default -> status.is4xxClientError() ? "Invalid request" : "Unexpected error";
        };
    }

    private static ResponseEntity<Object> badRequest(FieldError error) {
        return ResponseEntity.badRequest().body(new ApiError(400, ErrorCode.INVALID_REQUEST,
                "Invalid request", null, List.of(error), null));
    }

    private static String newErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
