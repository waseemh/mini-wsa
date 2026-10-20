package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.StatsGroup;

import java.time.Instant;
import java.util.List;

public interface EventRepository {

    /** Column (or expression) that events can be grouped by. */
    enum Dimension {
        CATEGORY("category"), ACTION("action"), CLIENT_IP("host(client_ip)"), PATH("path");

        final String sql;

        Dimension(String sql) {
            this.sql = sql;
        }
    }

    void saveAll(List<EnrichedEvent> events);

    /**
     * Event count and average threat score per {@code dimension}, busiest first, for events with
     * {@code from <= timestamp < to}. A null {@code configId} means all configurations.
     */
    List<StatsGroup> topGroups(Dimension dimension, Long configId, Instant from, Instant to, int limit);
}
