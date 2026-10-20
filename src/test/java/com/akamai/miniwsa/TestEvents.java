package com.akamai.miniwsa;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.GeoLocation;
import com.akamai.miniwsa.model.Rule;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.model.Severity;

import java.time.Instant;

public final class TestEvents {

    private TestEvents() {
    }

    public static SecurityEvent event(Severity severity, Action action, String path) {
        return event("evt-1", "203.0.113.42", Instant.parse("2026-05-20T14:32:10Z"), Category.INJECTION, severity, action, path);
    }

    public static SecurityEvent event(String id, String ip, Instant ts, Category category, Severity severity,
                                      Action action, String path) {
        return new SecurityEvent(id, ts, 14227L, "pol_web1", ip, "www.example.com", path, "POST", 403, "Mozilla/5.0",
                new Rule("950001", "SQL_INJECTION", "SQL Injection Attack Detected", severity, category),
                action, new GeoLocation("CN", "Beijing"), 1024L, 256L);
    }
}
