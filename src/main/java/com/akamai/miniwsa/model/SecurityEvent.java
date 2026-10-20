package com.akamai.miniwsa.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

/** A raw security event (DLR) as received on the ingestion API. */
public record SecurityEvent(
        @NotBlank String eventId,
        @NotNull @JsonDeserialize(using = IsoInstantDeserializer.class) Instant timestamp,
        @NotNull Long configId,
        String policyId,
        @NotBlank @ValidIp String clientIp,
        String hostname,
        @NotBlank String path,
        @NotBlank String method,
        @Min(100) @Max(599) Integer statusCode,
        String userAgent,
        @NotNull @Valid Rule rule,
        @NotNull Action action,
        @Valid GeoLocation geoLocation,
        @PositiveOrZero Long requestSize,
        @PositiveOrZero Long responseSize) {
}
