package com.akamai.miniwsa.enrich;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatOffenderTrackerTest {

    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");

    private final RepeatOffenderTracker tracker = new RepeatOffenderTracker();

    @Test
    void countsEarlierEventsFromSameIp() {
        assertThat(tracker.recordAndCount("1.1.1.1", T0)).isZero();
        assertThat(tracker.recordAndCount("1.1.1.1", T0.plusSeconds(10))).isEqualTo(1);
        assertThat(tracker.recordAndCount("1.1.1.1", T0.plusSeconds(20))).isEqualTo(2);
    }

    @Test
    void ipsAreIndependent() {
        tracker.recordAndCount("1.1.1.1", T0);
        assertThat(tracker.recordAndCount("2.2.2.2", T0.plusSeconds(1))).isZero();
    }

    @Test
    void eventsOlderThanTenMinutesDoNotCount() {
        tracker.recordAndCount("1.1.1.1", T0);
        assertThat(tracker.recordAndCount("1.1.1.1", T0.plusSeconds(600))).isEqualTo(1);   // exactly 10 min: still in
        assertThat(tracker.recordAndCount("1.1.1.1", T0.plusSeconds(601))).isEqualTo(1);   // T0 dropped, 600s remains
        assertThat(tracker.recordAndCount("1.1.1.1", T0.plusSeconds(2000))).isZero();
    }

    @Test
    void outOfOrderEventDoesNotCountLaterOnes() {
        tracker.recordAndCount("1.1.1.1", T0.plusSeconds(300));
        assertThat(tracker.recordAndCount("1.1.1.1", T0)).isZero();
    }
}
