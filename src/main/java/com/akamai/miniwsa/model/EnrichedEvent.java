package com.akamai.miniwsa.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.Instant;

/** An accepted event plus the fields the server adds; serialised as one flat object. */
public record EnrichedEvent(@JsonUnwrapped SecurityEvent event, Instant receivedAt, String attackType, int threatScore) {
}
