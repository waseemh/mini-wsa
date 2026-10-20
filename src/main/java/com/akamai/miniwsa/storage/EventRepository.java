package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.EnrichedEvent;

import java.util.List;

public interface EventRepository {

    void saveAll(List<EnrichedEvent> events);
}
