package com.akamai.miniwsa.generator;

import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.GeoLocation;
import com.akamai.miniwsa.model.Rule;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.model.Severity;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Generates realistic random security events: steady background traffic from a pool of clients plus
 * "attack waves", bursts of events from one IP against one path. The same seed always produces the
 * same events. Not thread-safe.
 */
public class EventGenerator {

    private record SiteConfig(long id, String hostname, String policyId) {
    }

    private record RuleTemplate(String id, String name, String message, Severity severity, Category category,
                                int weight) {
    }

    /** Share of all events that belong to attack waves. */
    private static final double WAVE_SHARE = 0.3;
    private static final int MIN_WAVE_SIZE = 30;
    private static final int MAX_WAVE_SIZE = 120;
    private static final int MAX_WAVE_GAP_MS = 5000;
    private static final int CLIENT_POOL_SIZE = 200;

    private static final List<SiteConfig> CONFIGS = List.of(
            new SiteConfig(14227, "www.example.com", "pol_web1"),
            new SiteConfig(14228, "api.example.com", "pol_api1"),
            new SiteConfig(20511, "shop.example.org", "pol_shop1"));

    private static final List<RuleTemplate> RULES = List.of(
            new RuleTemplate("950001", "SQL_INJECTION", "SQL Injection Attack Detected", Severity.CRITICAL, Category.INJECTION, 12),
            new RuleTemplate("950002", "COMMAND_INJECTION", "OS Command Injection Attempt", Severity.CRITICAL, Category.INJECTION, 5),
            new RuleTemplate("941100", "XSS_ATTACK", "Cross-Site Scripting Attack Detected", Severity.HIGH, Category.XSS, 10),
            new RuleTemplate("941110", "XSS_SCRIPT_TAG", "Script Tag in Request Parameter", Severity.MEDIUM, Category.XSS, 8),
            new RuleTemplate("920100", "INVALID_HTTP_REQUEST", "Malformed HTTP Request Line", Severity.MEDIUM, Category.PROTOCOL_VIOLATION, 8),
            new RuleTemplate("920270", "INVALID_CHARACTER", "Invalid Character in Request", Severity.LOW, Category.PROTOCOL_VIOLATION, 6),
            new RuleTemplate("950130", "SENSITIVE_FILE_ACCESS", "Attempt to Read Sensitive File", Severity.HIGH, Category.DATA_LEAKAGE, 6),
            new RuleTemplate("950100", "DATA_EXFIL_PATTERN", "Sensitive Data in Response", Severity.CRITICAL, Category.DATA_LEAKAGE, 3),
            new RuleTemplate("990001", "KNOWN_BAD_BOT", "Known Malicious Bot", Severity.MEDIUM, Category.BOT, 14),
            new RuleTemplate("990002", "SCRAPER_ACTIVITY", "Automated Scraping Detected", Severity.LOW, Category.BOT, 12),
            new RuleTemplate("912100", "HTTP_FLOOD", "Request Flood Detected", Severity.HIGH, Category.DOS, 5),
            new RuleTemplate("912110", "SLOW_REQUEST_ATTACK", "Slow Request Attack", Severity.MEDIUM, Category.DOS, 4),
            new RuleTemplate("960000", "RATE_LIMIT_EXCEEDED", "Client Exceeded Rate Limit", Severity.LOW, Category.RATE_LIMIT, 5),
            new RuleTemplate("960010", "LOGIN_RATE_LIMIT", "Too Many Login Attempts", Severity.MEDIUM, Category.RATE_LIMIT, 2));

    private static final List<String> BASELINE_PATHS = List.of("/", "/index.html", "/api/v1/products",
            "/api/v1/search", "/api/v1/users/me", "/static/app.js", "/checkout", "/contact", "/login",
            "/api/v1/login", "/admin");
    private static final List<String> ATTACK_PATHS = List.of("/api/v1/login", "/login", "/admin", "/admin/login",
            "/api/v1/search", "/api/v1/users", "/wp-login.php");
    private static final List<String> METHODS = List.of("GET", "GET", "GET", "POST", "PUT");
    private static final List<Integer> NON_DENY_STATUSES = List.of(200, 200, 200, 404, 500);
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
            "Mozilla/5.0 (X11; Linux x86_64)",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)",
            "curl/8.4.0", "python-requests/2.31.0", "Go-http-client/1.1");
    private static final List<GeoLocation> GEOS = List.of(
            new GeoLocation("CN", "Beijing"), new GeoLocation("RU", "Moscow"), new GeoLocation("US", "New York"),
            new GeoLocation("BR", "Sao Paulo"), new GeoLocation("DE", "Berlin"), new GeoLocation("IN", "Mumbai"),
            new GeoLocation("IR", "Tehran"), new GeoLocation("NG", "Lagos"), new GeoLocation("GB", "London"));
    private static final int[] FIRST_OCTETS = {5, 23, 31, 45, 62, 77, 89, 103, 118, 154, 185, 203};

    private final long seed;
    private final Random random;
    private final Instant from;
    private final long spanSeconds;
    private final List<String> clientPool = new ArrayList<>();
    private int sequence;

    /** Events fall in {@code [to - span, to]}; {@code span} must be at least one hour. */
    public EventGenerator(long seed, Instant to, Duration span) {
        if (span.compareTo(Duration.ofHours(1)) < 0) {
            throw new IllegalArgumentException("span must be at least 1 hour");
        }
        this.seed = seed;
        this.random = new Random(seed);
        this.from = to.truncatedTo(ChronoUnit.SECONDS).minus(span);
        this.spanSeconds = span.toSeconds();
        for (int i = 0; i < CLIENT_POOL_SIZE; i++) {
            clientPool.add(randomIp());
        }
    }

    /** Returns exactly {@code count} events in timestamp order, so they can be ingested chronologically. */
    public List<SecurityEvent> generate(int count) {
        List<SecurityEvent> events = new ArrayList<>(count);
        int waveEvents = (int) (count * WAVE_SHARE);
        while (events.size() < waveEvents) {
            int size = MIN_WAVE_SIZE + random.nextInt(MAX_WAVE_SIZE - MIN_WAVE_SIZE + 1);
            addWave(events, Math.min(size, waveEvents - events.size()));
        }
        while (events.size() < count) {
            events.add(backgroundEvent());
        }
        events.sort(Comparator.comparing(SecurityEvent::timestamp));
        return events;
    }

    private void addWave(List<SecurityEvent> out, int size) {
        String ip = randomIp();
        SiteConfig config = pick(CONFIGS);
        String path = pick(ATTACK_PATHS);
        RuleTemplate rule = weightedRule();
        // a wave lasts at most size * MAX_WAVE_GAP_MS (10 minutes), so leave room before the end of the range
        Instant at = from.plusSeconds(random.nextLong(spanSeconds - MAX_WAVE_SIZE * MAX_WAVE_GAP_MS / 1000));
        for (int i = 0; i < size; i++) {
            Action action = random.nextInt(10) < 8 ? Action.DENY : Action.ALERT;
            out.add(event(ip, config, at, path, rule, action));
            at = at.plusMillis(500 + random.nextInt(MAX_WAVE_GAP_MS - 500));
        }
    }

    private SecurityEvent backgroundEvent() {
        Instant at = from.plusSeconds(random.nextLong(spanSeconds + 1));
        Action action = switch (random.nextInt(10)) {
            case 0, 1, 2, 3 -> Action.DENY;
            case 4, 5, 6 -> Action.ALERT;
            default -> Action.MONITOR;
        };
        return event(pick(clientPool), pick(CONFIGS), at, pick(BASELINE_PATHS), weightedRule(), action);
    }

    private SecurityEvent event(String ip, SiteConfig config, Instant at, String path, RuleTemplate rule,
                                Action action) {
        int status = action != Action.DENY ? pick(NON_DENY_STATUSES)
                : rule.category() == Category.RATE_LIMIT ? 429 : 403;
        // a client keeps the same location and browser across events
        GeoLocation geo = GEOS.get(Math.floorMod(ip.hashCode(), GEOS.size()));
        String userAgent = USER_AGENTS.get(Math.floorMod(ip.hashCode() >>> 3, USER_AGENTS.size()));
        return new SecurityEvent("evt-%d-%06d".formatted(seed, ++sequence), at, config.id(), config.policyId(), ip,
                config.hostname(), path, pick(METHODS), status, userAgent,
                new Rule(rule.id(), rule.name(), rule.message(), rule.severity(), rule.category()), action, geo,
                (long) (200 + random.nextInt(5000)), (long) (100 + random.nextInt(20000)));
    }

    private RuleTemplate weightedRule() {
        int roll = random.nextInt(RULES.stream().mapToInt(RuleTemplate::weight).sum());
        for (RuleTemplate rule : RULES) {
            roll -= rule.weight();
            if (roll < 0) {
                return rule;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private String randomIp() {
        return FIRST_OCTETS[random.nextInt(FIRST_OCTETS.length)] + "." + random.nextInt(256) + "."
                + random.nextInt(256) + "." + (1 + random.nextInt(254));
    }

    private <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
}
