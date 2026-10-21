package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP calls from many threads against the running server, so Tomcat threads and the connection pool are involved. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyApiTest extends PostgresIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-09-01T00:00:00Z");

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private static String event(String id, long configId, String ip, int second) {
        return """
                {"eventId":"%s","timestamp":"%s","configId":%d,"clientIp":"%s","path":"/login","method":"POST",
                 "rule":{"id":"1","name":"R","severity":"HIGH","category":"%s"},"action":"DENY"}"""
                .formatted(id, BASE.plusSeconds(second), configId, ip, second % 2 == 0 ? "BOT" : "INJECTION");
    }

    private static String batch(String idPrefix, int count, long configId) {
        return IntStream.range(0, count)
                .mapToObj(i -> event(idPrefix + "-" + i, configId, "10.9.0." + (i % 5), i))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private HttpResponse<String> post(String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/events/ingest"))
                .header("Content-Type", "application/json").timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path + " -> " + response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }

    /** Runs all tasks at the same moment on separate threads and returns their results, rethrowing any failure. */
    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream().map(task -> pool.submit(() -> {
                start.await();
                return task.call();
            })).toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int count(long configId) {
        return jdbc.queryForObject("SELECT count(*) FROM events WHERE config_id = ?", Integer.class, configId);
    }

    @Test
    void parallelBatchesAreAllStoredExactlyOnce() throws Exception {
        long config = 90100;
        List<Callable<Integer>> clients = IntStream.range(0, 16).<Callable<Integer>>mapToObj(client -> () -> {
            int accepted = 0;
            for (int round = 0; round < 5; round++) {
                HttpResponse<String> response = post(batch("par-" + client + "-" + round, 100, config));
                assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
                accepted += mapper.readTree(response.body()).get("accepted").asInt();
            }
            return accepted;
        }).toList();

        int accepted = runTogether(clients).stream().mapToInt(Integer::intValue).sum();

        assertThat(accepted).isEqualTo(16 * 5 * 100);
        assertThat(count(config)).isEqualTo(16 * 5 * 100);
    }

    @Test
    void sameBatchPostedConcurrentlyIsStoredOnce() throws Exception {
        long config = 90101;
        String body = batch("same", 300, config);
        List<Callable<JsonNode>> clients = IntStream.range(0, 8).<Callable<JsonNode>>mapToObj(client -> () -> {
            HttpResponse<String> response = post(body);
            assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
            return mapper.readTree(response.body());
        }).toList();

        List<JsonNode> responses = runTogether(clients);

        // The database never holds a duplicate. The responses may over-report "accepted" when requests race on
        // the same ids (they check for existing ids before inserting), but every event is accounted for.
        assertThat(count(config)).isEqualTo(300);
        assertThat(responses).allSatisfy(r -> assertThat(r.get("accepted").asInt() + r.get("duplicates").asInt()).isEqualTo(300));
        assertThat(responses.stream().mapToInt(r -> r.get("accepted").asInt()).sum()).isGreaterThanOrEqualTo(300);
    }

    @Test
    void readsSeeOneConsistentSnapshotWhileWritesHappen() throws Exception {
        long config = 90102;
        List<Callable<List<String>>> tasks = new ArrayList<>();
        for (int writer = 0; writer < 4; writer++) {
            int w = writer;
            tasks.add(() -> {
                for (int round = 0; round < 40; round++) {
                    HttpResponse<String> response = post(batch("snap-" + w + "-" + round, 25, config));
                    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
                }
                return List.of();
            });
        }
        for (int reader = 0; reader < 3; reader++) {
            tasks.add(() -> {
                List<String> violations = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    JsonNode summary = get("/v1/stats/summary?configId=" + config
                            + "&from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z");
                    long total = summary.get("totalEvents").asLong();
                    long byAction = sum(summary.get("byAction"), false);
                    long byCategory = sum(summary.get("byCategory"), true);
                    if (total != byAction || total != byCategory) {
                        violations.add("summary total=" + total + " byAction=" + byAction + " byCategory=" + byCategory);
                    }

                    JsonNode samples = get("/v1/events/samples?configId=" + config + "&limit=100");
                    long expectedItems = Math.min(samples.get("total").asLong(), 100);
                    if (samples.get("items").size() != expectedItems) {
                        violations.add("samples total=" + samples.get("total") + " items=" + samples.get("items").size());
                    }
                }
                return violations;
            });
        }

        List<String> violations = runTogether(tasks).stream().flatMap(List::stream).toList();

        assertThat(violations).isEmpty();
        assertThat(count(config)).isEqualTo(4 * 40 * 25);
    }

    private static long sum(JsonNode counts, boolean nestedCount) {
        long sum = 0;
        for (JsonNode value : counts) {
            sum += nestedCount ? value.get("count").asLong() : value.asLong();
        }
        return sum;
    }
}
