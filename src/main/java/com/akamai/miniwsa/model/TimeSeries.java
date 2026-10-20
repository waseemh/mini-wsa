package com.akamai.miniwsa.model;

import com.akamai.miniwsa.model.StatsSummary.TimeRange;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** Event counts per time bucket, with empty buckets included as zero so the series has no gaps. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeSeries(Long configId, String interval, TimeRange timeRange, List<Bucket> buckets) {

    /** {@code start} is the bucket's inclusive start; it covers the following interval. */
    public record Bucket(Instant start, long count) {
    }
}
