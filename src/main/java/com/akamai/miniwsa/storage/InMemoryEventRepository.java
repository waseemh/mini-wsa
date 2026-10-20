package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.EnrichedEvent;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryEventRepository implements EventRepository {

    private final List<EnrichedEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void saveAll(List<EnrichedEvent> batch) {
        events.addAll(batch);
    }

    public List<EnrichedEvent> all() {
        return List.copyOf(events);
    }
}
