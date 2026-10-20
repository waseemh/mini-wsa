package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.api.error.ApiError.FieldError;
import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.storage.EventRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestService {

    private final Validator validator;
    private final EventRepository repository;
    private final Clock clock;

    public IngestService(Validator validator, EventRepository repository, Clock clock) {
        this.validator = validator;
        this.repository = repository;
        this.clock = clock;
    }

    /** Validates the whole batch; stores it only if every event is valid. Returns the number stored. */
    public int ingest(List<SecurityEvent> events) {
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            SecurityEvent event = events.get(i);
            if (event == null) {
                errors.add(new FieldError(i, "$", "event must not be null"));
                continue;
            }
            for (ConstraintViolation<SecurityEvent> v : validator.validate(event)) {
                errors.add(new FieldError(i, v.getPropertyPath().toString(), v.getMessage()));
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidEventsException(errors);
        }

        Instant receivedAt = clock.instant();
        repository.saveAll(events.stream().map(e -> new EnrichedEvent(e, receivedAt)).toList());
        return events.size();
    }
}
