package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.api.error.ApiError.FieldError;

import java.util.List;

public class InvalidEventsException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidEventsException(List<FieldError> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public List<FieldError> errors() {
        return errors;
    }
}
