package com.akamai.miniwsa.api.error;

import com.akamai.miniwsa.api.error.ApiError.FieldError;
import com.akamai.miniwsa.ingest.InvalidEventsException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidEventsException.class)
    ResponseEntity<ApiError> invalidEvents(InvalidEventsException ex) {
        return badRequest(ex.getMessage(), ex.errors());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> invalidRequest(InvalidRequestException ex) {
        return badRequest("Invalid request", List.of(new FieldError(null, ex.field(), ex.getMessage())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex) {
        return badRequest("Invalid request", List.of(
                new FieldError(null, ex.getParameterName(), "parameter is required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badParameter(MethodArgumentTypeMismatchException ex) {
        return badRequest("Invalid request", List.of(
                new FieldError(null, ex.getName(), "invalid value '" + ex.getValue() + "'")));
    }

    /** Malformed JSON, bad timestamp, or an enum value that does not exist. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof JsonMappingException jme) {
            return badRequest("Invalid request body", List.of(fieldError(jme)));
        }
        return badRequest("Malformed JSON request body", List.of());
    }

    private static FieldError fieldError(JsonMappingException ex) {
        Integer index = null;
        StringBuilder field = new StringBuilder();
        for (Reference ref : ex.getPath()) {
            if (ref.getFrom() instanceof List<?> && ref.getIndex() >= 0) {
                index = ref.getIndex();
            } else if (ref.getFieldName() != null) {
                field.append(field.isEmpty() ? "" : ".").append(ref.getFieldName());
            }
        }
        String detail = ex instanceof JsonProcessingException ? ex.getOriginalMessage() : ex.getMessage();
        return new FieldError(index, field.isEmpty() ? "$" : field.toString(), detail);
    }

    private static ResponseEntity<ApiError> badRequest(String message, List<FieldError> errors) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(HttpStatus.BAD_REQUEST.value(), message, errors));
    }
}
