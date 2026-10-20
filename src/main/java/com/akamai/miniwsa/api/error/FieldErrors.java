package com.akamai.miniwsa.api.error;

import com.akamai.miniwsa.api.error.ApiError.FieldError;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Builds {@link FieldError}s with messages a client can act on, without exposing Java or Jackson internals. */
public final class FieldErrors {

    private FieldErrors() {
    }

    public static FieldError required(Integer index, String field) {
        return new FieldError(index, field, ErrorCode.REQUIRED, "is required");
    }

    /** For a value that could not be converted to {@code type}. */
    public static FieldError wrongType(Integer index, String field, Class<?> type) {
        if (type != null && type.isEnum()) {
            String allowed = Arrays.stream(type.getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", "));
            return new FieldError(index, field, ErrorCode.INVALID_ENUM, "must be one of " + allowed);
        }
        if (type == Instant.class) {
            return new FieldError(index, field, ErrorCode.INVALID_FORMAT,
                    "must be an ISO 8601 timestamp with a zone, e.g. 2026-05-20T14:32:10Z");
        }
        if (type == Long.class || type == long.class || type == Integer.class || type == int.class) {
            return new FieldError(index, field, ErrorCode.INVALID_TYPE, "must be a whole number");
        }
        if (type == String.class) {
            return new FieldError(index, field, ErrorCode.INVALID_TYPE, "must be a string");
        }
        return new FieldError(index, field, ErrorCode.INVALID_TYPE, "has the wrong type");
    }
}
