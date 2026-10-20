package com.akamai.miniwsa.generator;

import com.akamai.miniwsa.enrich.RepeatOffenderTracker;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.SecurityEvent;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventGeneratorTest {

    private static final Instant TO = Instant.parse("2026-05-20T12:00:00Z");
    private static final Duration SPAN = Duration.ofHours(24);

    private List<SecurityEvent> generate(long seed, int count) {
        return new EventGenerator(seed, TO, SPAN).generate(count);
    }

    @Test
    void sameSeedGivesSameEvents() {
        assertThat(generate(7, 500)).isEqualTo(generate(7, 500));
        assertThat(generate(7, 500)).isNotEqualTo(generate(8, 500));
    }

    @Test
    void producesRequestedCountOfUniqueOrderedEventsInRange() {
        List<SecurityEvent> events = generate(1, 3000);

        assertThat(events).hasSize(3000);
        assertThat(events.stream().map(SecurityEvent::eventId).collect(Collectors.toSet())).hasSize(3000);
        assertThat(events).isSortedAccordingTo(Comparator.comparing(SecurityEvent::timestamp));
        assertThat(events).allSatisfy(e -> assertThat(e.timestamp()).isBetween(TO.minus(SPAN), TO));
    }

    @Test
    void handlesSmallCounts() {
        assertThat(generate(1, 0)).isEmpty();
        assertThat(generate(1, 1)).hasSize(1);
        assertThat(generate(1, 7)).hasSize(7);
    }

    @Test
    void everyEventPassesIngestionValidation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(generate(3, 2000)).allSatisfy(e -> assertThat(validator.validate(e)).isEmpty());
    }

    @Test
    void coversEveryCategory() {
        Set<Category> seen = generate(5, 5000).stream().map(e -> e.rule().category())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));
        assertThat(seen).containsExactlyInAnyOrder(Category.values());
    }

    @Test
    void containsAttackWavesFromOneIpAgainstOnePath() {
        Map<String, Long> eventsPerIpAndPath = generate(11, 5000).stream()
                .collect(Collectors.groupingBy(e -> e.clientIp() + " " + e.path(), Collectors.counting()));

        assertThat(Collections.max(eventsPerIpAndPath.values())).isGreaterThanOrEqualTo(30);
    }

    @Test
    void waveEventsTriggerTheRepeatOffenderBonus() {
        RepeatOffenderTracker.Batch window = new RepeatOffenderTracker().newBatch();
        long flagged = generate(11, 5000).stream()
                .filter(e -> window.countAndAdd(e.clientIp(), e.timestamp()) > 5)
                .count();

        assertThat(flagged).isPositive();
    }

    @Test
    void rejectsTooShortSpan() {
        assertThatThrownBy(() -> new EventGenerator(1, TO, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
