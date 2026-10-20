package com.akamai.miniwsa.model;

import com.akamai.miniwsa.api.error.InvalidRequestException;

import java.util.Arrays;

/** Supported time-series bucket sizes, aligned to the Unix epoch (so 1h buckets start on the hour). */
public enum BucketInterval {
    ONE_MINUTE("1m", 60), FIVE_MINUTES("5m", 300), ONE_HOUR("1h", 3600);

    private final String label;
    private final long seconds;

    BucketInterval(String label, long seconds) {
        this.label = label;
        this.seconds = seconds;
    }

    public String label() {
        return label;
    }

    public long seconds() {
        return seconds;
    }

    public static BucketInterval parse(String label) {
        return Arrays.stream(values()).filter(i -> i.label.equals(label)).findFirst()
                .orElseThrow(() -> new InvalidRequestException("interval", "must be one of 1m, 5m, 1h"));
    }
}
