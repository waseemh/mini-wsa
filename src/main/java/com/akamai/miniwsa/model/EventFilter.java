package com.akamai.miniwsa.model;

import java.time.Instant;

/** Optional criteria for selecting events; a null field means "don't filter on it". {@code from} is inclusive, {@code to} exclusive. */
public record EventFilter(Long configId, Instant from, Instant to, Category category, Action action) {
}
