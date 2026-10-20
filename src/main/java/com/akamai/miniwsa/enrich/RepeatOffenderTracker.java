package com.akamai.miniwsa.enrich;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding 10-minute window of event times per client IP, measured on event time (not wall clock)
 * so replayed or generated data behaves deterministically.
 *
 * Events are only recorded once a whole batch has been stored (see {@link Batch}), so a failed request
 * leaves no trace in the window.
 *
 * shortcut: in memory, per instance, empty after restart and idle IPs are never evicted;
 * move to Redis or a query on the events table if this runs on several nodes.
 */
@Component
public class RepeatOffenderTracker {

    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, List<Instant>> seen = new ConcurrentHashMap<>();

    /** How many recorded events from this IP fall in the 10 minutes up to {@code at}. */
    public int count(String clientIp, Instant at) {
        List<Instant> times = seen.get(clientIp);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            return countWithinWindow(times, at);
        }
    }

    public void record(String clientIp, Instant at) {
        List<Instant> times = seen.computeIfAbsent(clientIp, ip -> new ArrayList<>());
        synchronized (times) {
            times.add(at);
            Instant evictBefore = times.stream().max(Instant::compareTo).orElse(at).minus(WINDOW);
            times.removeIf(t -> t.isBefore(evictBefore));
        }
    }

    /** Events of one request: they count each other while it is processed, but only join the window on commit. */
    public Batch newBatch() {
        return new Batch();
    }

    public final class Batch {

        private final Map<String, List<Instant>> pending = new HashMap<>();

        /** Returns how many earlier events from this IP are in the window (stored or earlier in this batch), then adds this one. */
        public int countAndAdd(String clientIp, Instant at) {
            List<Instant> inBatch = pending.computeIfAbsent(clientIp, ip -> new ArrayList<>());
            int count = count(clientIp, at) + countWithinWindow(inBatch, at);
            inBatch.add(at);
            return count;
        }

        public void commit() {
            pending.forEach((ip, times) -> times.forEach(t -> record(ip, t)));
        }
    }

    private static int countWithinWindow(List<Instant> times, Instant at) {
        Instant windowStart = at.minus(WINDOW);
        return (int) times.stream().filter(t -> !t.isBefore(windowStart) && !t.isAfter(at)).count();
    }
}
