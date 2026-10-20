package com.akamai.miniwsa.model;

import java.util.List;

/** One page of matching events; {@code total} counts all matches, not just this page. */
public record SamplesPage(long total, int limit, int offset, List<EnrichedEvent> items) {
}
