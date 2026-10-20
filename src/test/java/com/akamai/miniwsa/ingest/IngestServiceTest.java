package com.akamai.miniwsa.ingest;

import com.akamai.miniwsa.TestEvents;
import com.akamai.miniwsa.enrich.ClassificationService;
import com.akamai.miniwsa.enrich.RepeatOffenderTracker;
import com.akamai.miniwsa.enrich.ThreatScoreService;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.model.Severity;
import com.akamai.miniwsa.storage.EventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestServiceTest {

    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EventRepository repository = mock(EventRepository.class);
    private IngestService service;

    @BeforeEach
    void setUp() {
        when(repository.existingIds(any())).thenReturn(Set.of());
        service = new IngestService(Validation.buildDefaultValidatorFactory().getValidator(),
                new EventBinder(mapper), repository, new ClassificationService(), new ThreatScoreService(),
                new RepeatOffenderTracker(), Clock.fixed(T0, ZoneOffset.UTC));
    }

    private SecurityEvent event(String id, int second) {
        return TestEvents.event(id, "9.9.9.9", T0.plusSeconds(second), Category.BOT, Severity.LOW, Action.MONITOR, "/");
    }

    private JsonNode batch(List<SecurityEvent> events) {
        return mapper.valueToTree(events);
    }

    private List<EnrichedEvent> saved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EnrichedEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void failedSaveLeavesNoTraceInTheRepeatOffenderWindow() {
        List<SecurityEvent> wave = IntStream.range(0, 8).mapToObj(i -> event("w-" + i, i)).toList();
        doThrow(new DataAccessResourceFailureException("db down")).when(repository).saveAll(any());
        assertThatThrownBy(() -> service.ingest(batch(wave))).isInstanceOf(DataAccessResourceFailureException.class);

        // had the failed wave been recorded, this event would look like the 9th from the IP and get the +15 bonus
        org.mockito.Mockito.reset(repository);
        when(repository.existingIds(any())).thenReturn(Set.of());
        doNothing().when(repository).saveAll(any());
        service.ingest(batch(List.of(event("after", 30))));

        assertThat(saved().get(0).threatScore()).isEqualTo(10);   // LOW only
    }

    @Test
    void eventsInOneBatchCountTowardsEachOther() {
        List<SecurityEvent> wave = IntStream.range(0, 8).mapToObj(i -> event("w-" + i, i)).toList();

        service.ingest(batch(wave));

        List<Integer> scores = saved().stream().map(EnrichedEvent::threatScore).toList();
        assertThat(scores).containsExactly(10, 10, 10, 10, 10, 10, 25, 25);   // bonus once more than 5 came before
    }

    @Test
    void skipsEventsThatAreAlreadyStoredOrRepeatedInTheBatch() {
        when(repository.existingIds(any())).thenReturn(Set.of("stored"));

        IngestService.Result result = service.ingest(batch(List.of(event("stored", 0), event("new", 1), event("new", 2))));

        assertThat(result).isEqualTo(new IngestService.Result(1, 2));
        assertThat(saved()).extracting(e -> e.event().eventId()).containsExactly("new");
    }

    @Test
    void storesNothingWhenEverythingIsADuplicate() {
        when(repository.existingIds(any())).thenReturn(Set.of("a"));

        assertThat(service.ingest(batch(List.of(event("a", 0))))).isEqualTo(new IngestService.Result(0, 1));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void invalidBatchIsNotStoredAndDoesNotTouchTheRepository() {
        JsonNode body = mapper.createArrayNode().add(mapper.createObjectNode());

        assertThatThrownBy(() -> service.ingest(body)).isInstanceOf(InvalidEventsException.class);
        verify(repository, never()).existingIds(any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void errorListIsCappedAndMarkedTruncated() {
        JsonNode body = mapper.valueToTree(Collections.nCopies(300, Map.of()));

        assertThatThrownBy(() -> service.ingest(body))
                .isInstanceOfSatisfying(InvalidEventsException.class, e -> {
                    assertThat(e.errors()).hasSize(IngestService.MAX_REPORTED_ERRORS);
                    assertThat(e.truncated()).isTrue();
                });
    }

    @Test
    void smallBadBatchIsNotMarkedTruncated() {
        JsonNode body = mapper.createArrayNode().add(mapper.createObjectNode());

        assertThatThrownBy(() -> service.ingest(body))
                .isInstanceOfSatisfying(InvalidEventsException.class, e -> assertThat(e.truncated()).isFalse());
    }
}
