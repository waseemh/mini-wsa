package com.akamai.miniwsa.stats;

import com.akamai.miniwsa.api.error.InvalidRequestException;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.StatsGroup;
import com.akamai.miniwsa.model.StatsSummary;
import com.akamai.miniwsa.model.StatsSummary.Attacker;
import com.akamai.miniwsa.model.StatsSummary.CategoryStats;
import com.akamai.miniwsa.model.StatsSummary.PathCount;
import com.akamai.miniwsa.model.StatsSummary.TimeRange;
import com.akamai.miniwsa.storage.EventRepository;
import com.akamai.miniwsa.storage.EventRepository.Dimension;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private static final int TOP_N = 10;

    private final EventRepository repository;

    public StatsService(EventRepository repository) {
        this.repository = repository;
    }

    public StatsSummary summary(Long configId, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new InvalidRequestException("from", "'from' must not be after 'to'");
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
}
