package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.api.error.ApiError.FieldError;
import com.akamai.miniwsa.api.error.ErrorCode;
import com.akamai.miniwsa.api.error.FieldErrors;
import com.akamai.miniwsa.model.SecurityEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Turns one JSON element into a {@link SecurityEvent}, describing in client terms why it cannot. */
@Component
public class EventBinder {

    private final ObjectMapper mapper;

    public EventBinder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Returns the event, or null after adding what is wrong with it to {@code errors}. */
    public SecurityEvent bind(JsonNode node, Integer index, List<FieldError> errors) {
        if (!node.isObject()) {
            errors.add(new FieldError(index, "$", ErrorCode.INVALID_TYPE, "must be an event object"));
            return null;
        }
        try {
            return mapper.treeToValue(node, SecurityEvent.class);
        } catch (JsonProcessingException e) {
            errors.add(describe(index, e));
            return null;
        }
    }

    private static FieldError describe(Integer index, JsonProcessingException e) {
        if (e instanceof MismatchedInputException mismatch) {
            return FieldErrors.wrongType(index, path(mismatch), mismatch.getTargetType());
        }
        String field = e instanceof JsonMappingException mapping ? path(mapping) : "$";
        return new FieldError(index, field, ErrorCode.INVALID_FORMAT, "has an invalid value");
    }

    private static String path(JsonMappingException e) {
        String path = e.getPath().stream().map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull).collect(Collectors.joining("."));
        return path.isEmpty() ? "$" : path;
    }
}
