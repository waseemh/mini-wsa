package com.akamai.miniwsa.api;

import com.akamai.miniwsa.model.BucketInterval;
import com.akamai.miniwsa.model.StatsSummary;
import com.akamai.miniwsa.model.TimeSeries;
import com.akamai.miniwsa.stats.StatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/v1/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** Aggregates for events with {@code from <= timestamp < to}; all configurations if configId is omitted. */
    @GetMapping("/summary")
    public StatsSummary summary(
            @RequestParam(required = false) Long configId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to) {
        return statsService.summary(configId, from, to);
    }

    /** Event counts per {@code interval} (1m, 5m or 1h) bucket, with empty buckets as zero, ready for a line chart. */
    @GetMapping("/timeseries")
    public TimeSeries timeSeries(
            @RequestParam(required = false) Long configId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to,
            @RequestParam String interval) {
        return statsService.timeSeries(configId, from, to, BucketInterval.parse(interval));
    }
}
