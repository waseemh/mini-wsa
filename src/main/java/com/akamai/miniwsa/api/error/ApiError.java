package com.akamai.miniwsa.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record ApiError(int status, String message, List<FieldError> errors) {

    /** {@code index} is the position in the batch; it is left out for problems that are not about a batch item. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(Integer index, String field, String message) {
    }
}
