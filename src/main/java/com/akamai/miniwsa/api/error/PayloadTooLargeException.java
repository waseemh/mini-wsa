package com.akamai.miniwsa.api.error;

/** The request is bigger than the service accepts; the client should send it in smaller pieces. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
