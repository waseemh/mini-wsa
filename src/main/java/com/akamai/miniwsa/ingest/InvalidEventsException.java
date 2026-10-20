package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.api.error.ApiError.FieldError;

import java.util.List;

public class InvalidEventsException extends RuntimeException {

    private final List<FieldError> errors;
    private final boolean truncated;

    /** @param truncated true if the list is cut short and more problems may exist */
    public InvalidEventsException(List<FieldError> errors, boolean truncated) {
        super("One or more events are invalid");
        this.errors = errors;
        this.truncated = truncated;
    }

    public List<FieldError> errors() {
        return errors;
    }

    public boolean truncated() {
        return truncated;
    }
}
