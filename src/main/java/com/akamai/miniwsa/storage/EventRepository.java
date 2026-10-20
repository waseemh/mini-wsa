package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.SamplesPage;
import com.akamai.miniwsa.model.StatsGroup;

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

    /** Event count and average threat score per {@code dimension}, busiest first. */
    List<StatsGroup> topGroups(Dimension dimension, EventFilter filter, int limit);

    /** Matching events, newest first, plus the total number of matches. */
    SamplesPage samples(EventFilter filter, int limit, int offset);
}
