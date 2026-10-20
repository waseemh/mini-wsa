package com.akamai.miniwsa.stats;

import com.akamai.miniwsa.api.error.ErrorCode;
import com.akamai.miniwsa.api.error.InvalidRequestException;
import com.akamai.miniwsa.model.BucketInterval;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.StatsGroup;
import com.akamai.miniwsa.model.StatsSummary;
import com.akamai.miniwsa.model.StatsSummary.Attacker;
import com.akamai.miniwsa.model.StatsSummary.CategoryStats;
import com.akamai.miniwsa.model.StatsSummary.PathCount;
import com.akamai.miniwsa.model.StatsSummary.TimeRange;
import com.akamai.miniwsa.model.TimeSeries;
import com.akamai.miniwsa.model.TimeSeries.Bucket;
import com.akamai.miniwsa.storage.EventRepository;
import com.akamai.miniwsa.storage.EventRepository.Dimension;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private static final int TOP_N = 10;
    private static final long MAX_BUCKETS = 5000;

    private final EventRepository repository;

    public StatsService(EventRepository repository) {
        this.repository = repository;
    }

    public StatsSummary summary(Long configId, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from", ErrorCode.OUT_OF_RANGE, "'from' must not be after 'to'");
        }

        EventFilter filter = new EventFilter(configId, from, to, null, null);

        // Categories and actions are small closed sets, so no limit is needed.
        List<StatsGroup> categories = repository.topGroups(Dimension.CATEGORY, filter, Integer.MAX_VALUE);
        List<StatsGroup> actions = repository.topGroups(Dimension.ACTION, filter, Integer.MAX_VALUE);
        List<StatsGroup> attackers = repository.topGroups(Dimension.CLIENT_IP, filter, TOP_N);
        List<StatsGroup> paths = repository.topGroups(Dimension.PATH, filter, TOP_N);

        Map<String, CategoryStats> byCategory = new LinkedHashMap<>();
        categories.forEach(g -> byCategory.put(g.key(), new CategoryStats(g.count(), g.avgThreatScore())));
        Map<String, Long> byAction = new LinkedHashMap<>();
        actions.forEach(g -> byAction.put(g.key(), g.count()));

        return new StatsSummary(
                configId,
                new TimeRange(from, to),
                categories.stream().mapToLong(StatsGroup::count).sum(),
                byCategory,
                byAction,
                attackers.stream().map(g -> new Attacker(g.key(), g.count(), g.avgThreatScore())).toList(),
                paths.stream().map(g -> new PathCount(g.key(), g.count())).toList());
    }

    /**
     * Counts per bucket for {@code from <= timestamp < to}. Buckets are aligned to the interval, so the
     * first one starts at or before {@code from} and only counts events from {@code from} on.
     */
    public TimeSeries timeSeries(Long configId, Instant from, Instant to, BucketInterval interval) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from", ErrorCode.OUT_OF_RANGE, "'from' must not be after 'to'");
        }
        long step = interval.seconds();
        long firstBucket = Math.floorDiv(from.getEpochSecond(), step) * step;
        if ((to.getEpochSecond() - firstBucket) / step + 1 > MAX_BUCKETS) {
            throw new InvalidRequestException("interval", ErrorCode.OUT_OF_RANGE,
                    "time range spans more than " + MAX_BUCKETS + " buckets; use a larger interval or a shorter range");
        }

        Map<Instant, Long> counts = repository.countsPerBucket(new EventFilter(configId, from, to, null, null), interval);
        List<Bucket> buckets = new ArrayList<>();
        for (Instant start = Instant.ofEpochSecond(firstBucket); start.isBefore(to); start = start.plusSeconds(step)) {
            buckets.add(new Bucket(start, counts.getOrDefault(start, 0L)));
        }
        return new TimeSeries(configId, interval.label(), new TimeRange(from, to), buckets);
    }
}
