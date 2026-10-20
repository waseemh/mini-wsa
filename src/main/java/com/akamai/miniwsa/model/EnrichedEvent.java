package com.akamai.miniwsa.model;

import java.time.Instant;

/** An accepted event plus the fields the server adds. */
public record EnrichedEvent(SecurityEvent event, Instant receivedAt, String attackType, int threatScore) {
}
