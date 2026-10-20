package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SamplesApiTest extends PostgresIntegrationTest {

    private static final String CONFIG = "configId=90010";

    @Autowired MockMvc mvc;

    @BeforeAll
    static void ingestFixtures(@Autowired MockMvc mvc) throws Exception {
        String body = "[" + String.join(",",
                event("a1", "00:01", "INJECTION", "DENY"),
                event("a2", "00:02", "INJECTION", "ALERT"),
                event("a3", "00:03", "BOT", "DENY"),
                event("a4", "00:04", "XSS", "MONITOR"),
                event("a5", "00:05", "BOT", "MONITOR")) + "]";
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static String event(String id, String time, String category, String action) {
        return """
                {"eventId":"%s","timestamp":"2026-07-01T%s:00Z","configId":90010,"clientIp":"10.1.0.%s",
                 "path":"/home","method":"GET","statusCode":403,"userAgent":"curl",
                 "rule":{"id":"1","name":"R","message":"msg","severity":"HIGH","category":"%s"},
                 "action":"%s","geoLocation":{"country":"CN","city":"Beijing"},"requestSize":10,"responseSize":20}"""
                .formatted(id, time, id.substring(1), category, action);
    }

    private ResultActions samples(String query) throws Exception {
        return mvc.perform(get("/v1/events/samples?" + query));
    }

    @Test
    void returnsNewestFirstWithTotalAndDefaults() throws Exception {
        samples(CONFIG).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a5", "a4", "a3", "a2", "a1")));
    }

    @Test
    void returnsEnrichedEventAsFlatObject() throws Exception {
        samples(CONFIG + "&category=BOT&action=DENY").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventId").value("a3"))
                .andExpect(jsonPath("$.items[0].timestamp").value("2026-07-01T00:03:00Z"))
                .andExpect(jsonPath("$.items[0].clientIp").value("10.1.0.3"))
                .andExpect(jsonPath("$.items[0].rule.category").value("BOT"))
                .andExpect(jsonPath("$.items[0].geoLocation.city").value("Beijing"))
                .andExpect(jsonPath("$.items[0].requestSize").value(10))
                .andExpect(jsonPath("$.items[0].attackType").value("Bot Activity"))
                .andExpect(jsonPath("$.items[0].threatScore").value(50))   // 30 + 20
                .andExpect(jsonPath("$.items[0].receivedAt").isNotEmpty());
    }

    @Test
    void paginates() throws Exception {
        samples(CONFIG + "&limit=2&offset=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.offset").value(2))
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a3", "a2")));
        samples(CONFIG + "&limit=2&offset=4")
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a1")));
        samples(CONFIG + "&offset=10")
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void filtersByCategoryAndAction() throws Exception {
        samples(CONFIG + "&category=BOT")
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a5", "a3")));
        samples(CONFIG + "&action=DENY")
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a3", "a1")));
        samples(CONFIG + "&category=BOT&action=DENY")
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a3")));
    }

    @Test
    void filtersByTimeRangeWithExclusiveUpperBound() throws Exception {
        samples(CONFIG + "&from=2026-07-01T00:02:00Z&to=2026-07-01T00:04:00Z")
                .andExpect(jsonPath("$.items[*].eventId").value(contains("a3", "a2")));
        samples("configId=1&from=2026-07-01T00:00:00Z").andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rejectsBadParameters() throws Exception {
        samples(CONFIG + "&limit=101").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        samples(CONFIG + "&limit=0").andExpect(status().isBadRequest());
        samples(CONFIG + "&offset=-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("offset"));
        samples(CONFIG + "&category=NOPE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        samples(CONFIG + "&action=NOPE").andExpect(status().isBadRequest());
        samples(CONFIG + "&from=yesterday").andExpect(status().isBadRequest());
        samples("from=2026-07-02T00:00:00Z&to=2026-07-01T00:00:00Z").andExpect(status().isBadRequest());
    }
}
