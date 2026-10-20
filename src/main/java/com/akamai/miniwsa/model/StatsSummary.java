package com.akamai.miniwsa.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsSummary(
        Long configId,
        TimeRange timeRange,
        long totalEvents,
        Map<String, CategoryStats> byCategory,
        Map<String, Long> byAction,
        List<Attacker> topAttackers,
        List<PathCount> topTargetedPaths) {

    public record TimeRange(Instant from, Instant to) {
    }

    public record CategoryStats(long count, double avgThreatScore) {
    }

    public record Attacker(String clientIp, long count, double avgThreatScore) {
    }

    public record PathCount(String path, long count) {
    }
}
