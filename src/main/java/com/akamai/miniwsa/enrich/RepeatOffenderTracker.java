package com.akamai.miniwsa.enrich;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding 10-minute window of event times per client IP, measured on event time (not wall clock)
 * so replayed or generated data behaves deterministically.
 *
 * shortcut: in memory, per instance, empty after restart and idle IPs are never evicted;
 * move to Redis or a query on the events table if this runs on several nodes.
 */
@Component
public class RepeatOffenderTracker {

    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, List<Instant>> seen = new ConcurrentHashMap<>();

    /** Returns how many earlier events from this IP fall in the 10 minutes up to {@code at}, then records this one. */
    public int recordAndCount(String clientIp, Instant at) {
        List<Instant> times = seen.computeIfAbsent(clientIp, ip -> new ArrayList<>());
        synchronized (times) {
            Instant windowStart = at.minus(WINDOW);
            int count = (int) times.stream().filter(t -> !t.isBefore(windowStart) && !t.isAfter(at)).count();
            times.add(at);
            Instant evictBefore = times.stream().max(Instant::compareTo).orElse(at).minus(WINDOW);
            times.removeIf(t -> t.isBefore(evictBefore));
            return count;
        }
    }
}
