package com.akamai.miniwsa.generator;

import com.akamai.miniwsa.model.SecurityEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line entry point for the generator (see scripts/generate.sh).
 *
 * <pre>
 *   --count N        number of events (default 10000)
 *   --seed N         random seed; same seed, same events (default 42)
 *   --hours N        length of the time range in hours (default 24)
 *   --to ISO_TIME    end of the time range (default now)
 *   --out FILE       write a JSON array to FILE, "-" for stdout (default); the file can be POSTed as is
 *   --post URL       instead of writing, POST batches to URL/v1/events/ingest
 *   --batch-size N   events per POST (default 500)
 * </pre>
 */
public final class GeneratorRunner {

    private GeneratorRunner() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parse(args);
        int count = Integer.parseInt(options.getOrDefault("count", "10000"));
        long seed = Long.parseLong(options.getOrDefault("seed", "42"));
        Duration span = Duration.ofHours(Long.parseLong(options.getOrDefault("hours", "24")));
        Instant to = options.containsKey("to") ? Instant.parse(options.get("to"))
                : Instant.now().truncatedTo(ChronoUnit.SECONDS);

        List<SecurityEvent> events = new EventGenerator(seed, to, span).generate(count);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        if (options.containsKey("post")) {
            post(mapper, events, options.get("post"), Integer.parseInt(options.getOrDefault("batch-size", "500")));
        } else if ("-".equals(options.getOrDefault("out", "-"))) {
            mapper.writeValue(System.out, events);
        } else {
            mapper.writeValue(Path.of(options.get("out")).toFile(), events);
            System.err.printf("Wrote %d events to %s%n", events.size(), options.get("out"));
        }
    }

    private static void post(ObjectMapper mapper, List<SecurityEvent> events, String baseUrl, int batchSize)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/events/ingest");
        for (int i = 0; i < events.size(); i += batchSize) {
            List<SecurityEvent> batch = events.subList(i, Math.min(i + batchSize, events.size()));
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(batch)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                System.err.printf("Batch starting at %d failed with HTTP %d: %s%n", i, response.statusCode(), response.body());
                System.exit(1);
            }
            System.err.printf("Sent %d/%d events%n", i + batch.size(), events.size());
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected '--name value' pairs, got: " + args[i]);
            }
            options.put(args[i].substring(2), args[i + 1]);
        }
        return options;
    }
}
