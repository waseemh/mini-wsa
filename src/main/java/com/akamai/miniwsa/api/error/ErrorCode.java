package com.akamai.miniwsa.api.error;

/** Stable, machine-readable error identifiers; clients should branch on these, not on message text. */
public enum ErrorCode {
    // whole-response codes
    VALIDATION_FAILED, INVALID_REQUEST, MALFORMED_REQUEST, NOT_FOUND, METHOD_NOT_ALLOWED, NOT_ACCEPTABLE,
    PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, SERVICE_UNAVAILABLE, INTERNAL_ERROR,
    // per-field codes
    REQUIRED, INVALID_ENUM, INVALID_FORMAT, INVALID_TYPE, OUT_OF_RANGE;

    public static ErrorCode ofStatus(int status) {
        return switch (status) {
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> status >= 500 ? INTERNAL_ERROR : INVALID_REQUEST;
        };
    }
}
