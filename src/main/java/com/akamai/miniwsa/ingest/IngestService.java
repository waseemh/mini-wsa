package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.api.error.ApiError.FieldError;
import com.akamai.miniwsa.enrich.ClassificationService;
import com.akamai.miniwsa.enrich.RepeatOffenderTracker;
import com.akamai.miniwsa.enrich.ThreatScoreService;
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
    private final ClassificationService classifier;
    private final ThreatScoreService scorer;
    private final RepeatOffenderTracker offenders;
    private final Clock clock;

    public IngestService(Validator validator, EventRepository repository, ClassificationService classifier,
                         ThreatScoreService scorer, RepeatOffenderTracker offenders, Clock clock) {
        this.validator = validator;
        this.repository = repository;
        this.classifier = classifier;
        this.scorer = scorer;
        this.offenders = offenders;
        this.clock = clock;
    }

    /** Validates the whole batch, then enriches and stores it; nothing is stored if any event is invalid. Returns the number stored. */
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
        repository.saveAll(events.stream().map(e -> enrich(e, receivedAt)).toList());
        return events.size();
    }

    private EnrichedEvent enrich(SecurityEvent event, Instant receivedAt) {
        int recent = offenders.recordAndCount(event.clientIp(), event.timestamp());
        return new EnrichedEvent(event, receivedAt, classifier.attackType(event), scorer.score(event, recent));
    }
}
