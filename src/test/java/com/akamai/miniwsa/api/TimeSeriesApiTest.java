package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimeSeriesApiTest extends PostgresIntegrationTest {

    private static final String CONFIG = "configId=90020";

    @Autowired MockMvc mvc;

    @BeforeAll
    static void ingestFixtures(@Autowired MockMvc mvc) throws Exception {
        String body = "[" + String.join(",",
                event("t1", "00:01:00"), event("t2", "00:02:30"),
                event("t3", "00:07:10"), event("t4", "00:07:50"), event("t5", "00:07:59"),
                event("t6", "01:30:00")) + "]";
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static String event(String id, String time) {
        return """
                {"eventId":"%s","timestamp":"2026-08-01T%sZ","configId":90020,"clientIp":"10.2.0.%s","path":"/",
                 "method":"GET","rule":{"id":"1","name":"R","severity":"LOW","category":"BOT"},"action":"MONITOR"}"""
                .formatted(id, time, id.substring(1));
    }

    private ResultActions series(String query) throws Exception {
        return mvc.perform(get("/v1/stats/timeseries?" + query));
    }

    @Test
    void bucketsByHour() throws Exception {
        series(CONFIG + "&from=2026-08-01T00:00:00Z&to=2026-08-01T02:00:00Z&interval=1h")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(90020))
                .andExpect(jsonPath("$.interval").value("1h"))
                .andExpect(jsonPath("$.buckets[*].start").value(contains("2026-08-01T00:00:00Z", "2026-08-01T01:00:00Z")))
                .andExpect(jsonPath("$.buckets[*].count").value(contains(5, 1)));
    }

    @Test
    void bucketsByFiveMinutesAndFillsGapsWithZero() throws Exception {
        series(CONFIG + "&from=2026-08-01T00:00:00Z&to=2026-08-01T00:15:00Z&interval=5m")
                .andExpect(jsonPath("$.buckets[*].start")
                        .value(contains("2026-08-01T00:00:00Z", "2026-08-01T00:05:00Z", "2026-08-01T00:10:00Z")))
                .andExpect(jsonPath("$.buckets[*].count").value(contains(2, 3, 0)));
    }

    @Test
    void bucketsByMinute() throws Exception {
        series(CONFIG + "&from=2026-08-01T00:00:00Z&to=2026-08-01T00:03:00Z&interval=1m")
                .andExpect(jsonPath("$.buckets[*].count").value(contains(0, 1, 1)));
    }

    @Test
    void firstBucketIsAlignedButOnlyCountsEventsFromFrom() throws Exception {
        series(CONFIG + "&from=2026-08-01T00:02:00Z&to=2026-08-01T00:08:00Z&interval=5m")
                .andExpect(jsonPath("$.buckets[*].start").value(contains("2026-08-01T00:00:00Z", "2026-08-01T00:05:00Z")))
                .andExpect(jsonPath("$.buckets[*].count").value(contains(1, 3)));   // t1 is before 'from'
    }

    @Test
    void unknownConfigurationIsAllZeroes() throws Exception {
        series("configId=1&from=2026-08-01T00:00:00Z&to=2026-08-01T00:10:00Z&interval=5m")
                .andExpect(jsonPath("$.buckets", hasSize(2)))
                .andExpect(jsonPath("$.buckets[*].count").value(contains(0, 0)));
    }

    @Test
    void rejectsBadParameters() throws Exception {
        String range = "&from=2026-08-01T00:00:00Z&to=2026-08-01T01:00:00Z";
        series(CONFIG + range + "&interval=10m").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("interval"));
        series(CONFIG + range).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("interval"));
        series(CONFIG + "&from=2026-08-01T01:00:00Z&to=2026-08-01T00:00:00Z&interval=1m")
                .andExpect(status().isBadRequest());
        series(CONFIG + "&from=2020-01-01T00:00:00Z&to=2026-01-01T00:00:00Z&interval=1m")   // millions of buckets
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("interval"));
    }
}
