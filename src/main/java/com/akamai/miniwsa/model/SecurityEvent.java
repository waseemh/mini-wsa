package com.akamai.miniwsa.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** A raw security event (DLR) as received on the ingestion API. */
public record SecurityEvent(
        @NotBlank String eventId,
        @NotNull Instant timestamp,
        @NotNull Long configId,
        String policyId,
        @NotBlank String clientIp,
        String hostname,
        @NotBlank String path,
        @NotBlank String method,
        Integer statusCode,
        String userAgent,
        @NotNull @Valid Rule rule,
        @NotNull Action action,
        @Valid GeoLocation geoLocation,
        @PositiveOrZero Long requestSize,
        @PositiveOrZero Long responseSize) {
}
