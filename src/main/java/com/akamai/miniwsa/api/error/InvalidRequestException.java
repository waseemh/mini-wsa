package com.akamai.miniwsa.api.error;

/** A request whose parameters are individually well-formed but together make no sense. */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
