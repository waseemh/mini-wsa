package com.akamai.miniwsa.api.error;

import java.util.List;

public record ApiError(int status, String message, List<FieldError> errors) {

    /** {@code index} is the position in the batch, or null for non-batch problems. */
    public record FieldError(Integer index, String field, String message) {
    }
}
