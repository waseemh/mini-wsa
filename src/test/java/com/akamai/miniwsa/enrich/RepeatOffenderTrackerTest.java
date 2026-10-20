package com.akamai.miniwsa.enrich;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatOffenderTrackerTest {

    private static final Instant T0 = Instant.parse("2026-05-20T14:00:00Z");

    private final RepeatOffenderTracker tracker = new RepeatOffenderTracker();

    /** Counts earlier events, then records this one: what ingesting an event does. */
    private int seen(String ip, Instant at) {
        int count = tracker.count(ip, at);
        tracker.record(ip, at);
        return count;
    }

    @Test
    void countsEarlierEventsFromSameIp() {
        assertThat(seen("1.1.1.1", T0)).isZero();
        assertThat(seen("1.1.1.1", T0.plusSeconds(10))).isEqualTo(1);
        assertThat(seen("1.1.1.1", T0.plusSeconds(20))).isEqualTo(2);
    }

    @Test
    void ipsAreIndependent() {
        seen("1.1.1.1", T0);
        assertThat(seen("2.2.2.2", T0.plusSeconds(1))).isZero();
    }

    @Test
    void eventsOlderThanTenMinutesDoNotCount() {
        seen("1.1.1.1", T0);
        assertThat(seen("1.1.1.1", T0.plusSeconds(600))).isEqualTo(1);   // exactly 10 min: still in
        assertThat(seen("1.1.1.1", T0.plusSeconds(601))).isEqualTo(1);   // T0 dropped, 600s remains
        assertThat(seen("1.1.1.1", T0.plusSeconds(2000))).isZero();
    }

    @Test
    void outOfOrderEventDoesNotCountLaterOnes() {
        seen("1.1.1.1", T0.plusSeconds(300));
        assertThat(seen("1.1.1.1", T0)).isZero();
    }

    @Test
    void batchEventsCountEachOtherButNotTheWindowUntilCommitted() {
        RepeatOffenderTracker.Batch batch = tracker.newBatch();

        assertThat(batch.countAndAdd("1.1.1.1", T0)).isZero();
        assertThat(batch.countAndAdd("1.1.1.1", T0.plusSeconds(10))).isEqualTo(1);
        assertThat(tracker.count("1.1.1.1", T0.plusSeconds(20))).isZero();   // not committed yet

        batch.commit();
        assertThat(tracker.count("1.1.1.1", T0.plusSeconds(20))).isEqualTo(2);
    }

    @Test
    void batchSeesAlreadyCommittedEvents() {
        seen("1.1.1.1", T0);
        RepeatOffenderTracker.Batch batch = tracker.newBatch();

        assertThat(batch.countAndAdd("1.1.1.1", T0.plusSeconds(5))).isEqualTo(1);
        assertThat(batch.countAndAdd("1.1.1.1", T0.plusSeconds(6))).isEqualTo(2);
    }
}
