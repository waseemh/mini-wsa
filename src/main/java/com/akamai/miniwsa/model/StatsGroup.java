package com.akamai.miniwsa.model;

/** One GROUP BY row: how many events share {@code key} and their average threat score. */
public record StatsGroup(String key, long count, double avgThreatScore) {
}
