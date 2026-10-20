package com.akamai.miniwsa.api.error;

/** A request whose parameters are individually well-formed but together make no sense. */
public class InvalidRequestException extends RuntimeException {

    private final String field;
    private final ErrorCode code;

    public InvalidRequestException(String field, ErrorCode code, String message) {
        super(message);
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public ErrorCode code() {
        return code;
    }
}
