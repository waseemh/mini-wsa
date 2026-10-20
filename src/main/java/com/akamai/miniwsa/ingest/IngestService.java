package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.api.error.ApiError.FieldError;
import com.akamai.miniwsa.api.error.ErrorCode;
import com.akamai.miniwsa.api.error.FieldErrors;
import com.akamai.miniwsa.enrich.ClassificationService;
import com.akamai.miniwsa.enrich.RepeatOffenderTracker;
import com.akamai.miniwsa.enrich.ThreatScoreService;
import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.storage.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IngestService {

    static final int MAX_REPORTED_ERRORS = 100;

    /** @param accepted events stored; @param duplicates events skipped because their eventId already exists */
    public record Result(int accepted, int duplicates) {
    }

    private final Validator validator;
    private final EventBinder binder;
    private final EventRepository repository;
    private final ClassificationService classifier;
    private final ThreatScoreService scorer;
    private final RepeatOffenderTracker offenders;
    private final Clock clock;

    public IngestService(Validator validator, EventBinder binder, EventRepository repository,
                         ClassificationService classifier, ThreatScoreService scorer,
                         RepeatOffenderTracker offenders, Clock clock) {
        this.validator = validator;
        this.binder = binder;
        this.repository = repository;
        this.classifier = classifier;
        this.scorer = scorer;
        this.offenders = offenders;
        this.clock = clock;
    }

    /**
     * Ingests one event or an array of events. All-or-nothing: if any event is invalid nothing is stored and
     * the problems of every event are reported. Events whose eventId is already stored are skipped.
     */
    public Result ingest(JsonNode body) {
        List<SecurityEvent> events = parseAndValidate(body);

        Set<String> stored = repository.existingIds(events.stream().map(SecurityEvent::eventId).toList());
        Set<String> inBatch = new HashSet<>();
        List<SecurityEvent> fresh = events.stream()
                .filter(e -> !stored.contains(e.eventId()) && inBatch.add(e.eventId()))
                .toList();

        if (!fresh.isEmpty()) {
            store(fresh);
        }
        return new Result(fresh.size(), events.size() - fresh.size());
    }

    private void store(List<SecurityEvent> events) {
        Instant receivedAt = clock.instant();
        RepeatOffenderTracker.Batch window = offenders.newBatch();
        List<EnrichedEvent> enriched = new ArrayList<>(events.size());
        for (SecurityEvent event : events) {
            int recent = window.countAndAdd(event.clientIp(), event.timestamp());
            enriched.add(new EnrichedEvent(event, receivedAt, classifier.attackType(event),
                    scorer.score(event, recent)));
        }
        repository.saveAll(enriched);
        // only now do these events count towards later ones; a failed save leaves the window untouched
        window.commit();
    }

    private List<SecurityEvent> parseAndValidate(JsonNode body) {
        if (!body.isArray() && !body.isObject()) {
            throw new InvalidEventsException(List.of(new FieldError(null, "$", ErrorCode.INVALID_TYPE,
                    "request body must be an event or an array of events")), false);
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (body.isArray()) {
            body.forEach(nodes::add);
        } else {
            nodes.add(body);
        }

        List<FieldError> errors = new ArrayList<>();
        List<SecurityEvent> events = new ArrayList<>(nodes.size());
        boolean truncated = false;
        for (int i = 0; i < nodes.size(); i++) {
            Integer index = body.isArray() ? i : null;
            SecurityEvent event = binder.bind(nodes.get(i), index, errors);
            if (event != null) {
                validate(event, index, errors);
            }
            events.add(event);
            if (errors.size() >= MAX_REPORTED_ERRORS && i < nodes.size() - 1) {
                truncated = true;   // enough to go on; don't spend time listing every problem of a huge bad batch
                break;
            }
        }
        if (errors.size() > MAX_REPORTED_ERRORS) {
            errors = new ArrayList<>(errors.subList(0, MAX_REPORTED_ERRORS));
            truncated = true;
        }
        if (!errors.isEmpty()) {
            throw new InvalidEventsException(errors, truncated);
        }
        return events;
    }

    private void validate(SecurityEvent event, Integer index, List<FieldError> errors) {
        validator.validate(event).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(v -> toFieldError(index, v))
                .forEach(errors::add);
    }

    private static FieldError toFieldError(Integer index, ConstraintViolation<SecurityEvent> violation) {
        String field = violation.getPropertyPath().toString();
        Class<?> constraint = violation.getConstraintDescriptor().getAnnotation().annotationType();
        if (constraint == NotNull.class || constraint == NotBlank.class) {
            return FieldErrors.required(index, field);
        }
        ErrorCode code = constraint == Min.class || constraint == Max.class || constraint == PositiveOrZero.class
                ? ErrorCode.OUT_OF_RANGE : ErrorCode.INVALID_FORMAT;
        return new FieldError(index, field, code, violation.getMessage());
    }
}
