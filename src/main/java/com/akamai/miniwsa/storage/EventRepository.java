package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.BucketInterval;
import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.SamplesPage;
import com.akamai.miniwsa.model.StatsGroup;

import java.time.Instant;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface EventRepository {

    /** Column (or expression) that events can be grouped by. */
    enum Dimension {
        CATEGORY("category"), ACTION("action"), CLIENT_IP("host(client_ip)"), PATH("path");

        final String sql;

        Dimension(String sql) {
            this.sql = sql;
        }
    }

    /** Stores the events; one whose eventId already exists is skipped. */
    void saveAll(List<EnrichedEvent> events);

    /** The subset of {@code eventIds} that are already stored. */
    Set<String> existingIds(Collection<String> eventIds);

    /** Event count and average threat score per {@code dimension}, busiest first. */
    List<StatsGroup> topGroups(Dimension dimension, EventFilter filter, int limit);

    /** Matching events, newest first, plus the total number of matches. */
    SamplesPage samples(EventFilter filter, int limit, int offset);

    /** Event count per non-empty time bucket, keyed by bucket start. */
    Map<Instant, Long> countsPerBucket(EventFilter filter, BucketInterval interval);
}
