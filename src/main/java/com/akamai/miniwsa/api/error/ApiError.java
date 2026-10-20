package com.akamai.miniwsa.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The body of every error response.
 *
 * @param errorId   set for unexpected failures (5xx); the same id is in the server log
 * @param errors    what is wrong, field by field; absent when there is nothing more specific to say
 * @param truncated true when more problems may exist than {@code errors} lists
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, ErrorCode code, String message, String errorId, List<FieldError> errors,
                       Boolean truncated) {

    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(status, code, message, null, null, null);
    }

    /** {@code index} is the position in a batch; it is left out when the request is not an array. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(Integer index, String field, ErrorCode code, String message) {
    }
}
