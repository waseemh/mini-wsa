package com.akamai.miniwsa.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Accepts only ISO 8601 date-times with a zone or offset, as a string ("2026-05-20T14:32:10Z"); never epoch numbers. */
public class IsoInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (Instant) context.handleUnexpectedToken(Instant.class, parser);
        }
        String text = parser.getText().trim();
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            return (Instant) context.handleWeirdStringValue(Instant.class, text,
                    "expected an ISO 8601 timestamp such as 2026-05-20T14:32:10Z");
        }
    }
}
