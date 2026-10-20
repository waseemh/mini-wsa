package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsApiTest extends PostgresIntegrationTest {

    private static final long CONFIG = 90001;
    private static final String RANGE = "from=2026-06-01T00:00:00Z&to=2026-06-01T01:00:00Z";

    @Autowired MockMvc mvc;

    @BeforeAll
    static void ingestFixtures(@Autowired MockMvc mvc) throws Exception {
        String body = "[" + String.join(",",
                // scores: 30+20+15 = 65, 65, 10+10 = 20, 20+0+15 = 35
                event("s-1", CONFIG, "10.0.0.1", "2026-06-01T00:10:00Z", "HIGH", "INJECTION", "DENY", "/login"),
                event("s-2", CONFIG, "10.0.0.1", "2026-06-01T00:20:00Z", "HIGH", "INJECTION", "DENY", "/login"),
                event("s-3", CONFIG, "10.0.0.2", "2026-06-01T00:30:00Z", "LOW", "BOT", "ALERT", "/home"),
                event("s-4", CONFIG, "10.0.0.1", "2026-06-01T00:40:00Z", "MEDIUM", "XSS", "MONITOR", "/admin"),
                // outside the time range (the upper bound is exclusive) and another config
                event("s-5", CONFIG, "10.0.0.1", "2026-06-01T01:00:00Z", "HIGH", "DOS", "DENY", "/home"),
                event("s-6", 90002, "10.0.0.3", "2026-06-01T00:15:00Z", "HIGH", "DOS", "DENY", "/home")) + "]";
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    static String event(String id, long configId, String ip, String ts, String severity, String category,
                        String action, String path) {
        return """
                {"eventId":"%s","timestamp":"%s","configId":%d,"clientIp":"%s","path":"%s","method":"GET",
                 "rule":{"id":"1","name":"R","severity":"%s","category":"%s"},"action":"%s"}"""
                .formatted(id, ts, configId, ip, path, severity, category, action);
    }

    private ResultActions summary(String query) throws Exception {
        return mvc.perform(get("/v1/stats/summary?" + query));
    }

    @Test
    void summarisesOneConfigurationAndTimeRange() throws Exception {
        summary("configId=" + CONFIG + "&" + RANGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(CONFIG))
                .andExpect(jsonPath("$.timeRange.from").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.totalEvents").value(4))
                .andExpect(jsonPath("$.byCategory.INJECTION.count").value(2))
                .andExpect(jsonPath("$.byCategory.INJECTION.avgThreatScore").value(65.0))
                .andExpect(jsonPath("$.byCategory.BOT.count").value(1))
                .andExpect(jsonPath("$.byCategory.DOS").doesNotExist())
                .andExpect(jsonPath("$.byAction.DENY").value(2))
                .andExpect(jsonPath("$.byAction.ALERT").value(1))
                .andExpect(jsonPath("$.byAction.MONITOR").value(1))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("10.0.0.1"))
                .andExpect(jsonPath("$.topAttackers[0].count").value(3))
                .andExpect(jsonPath("$.topAttackers[0].avgThreatScore").value(55.0))
                .andExpect(jsonPath("$.topAttackers[1].clientIp").value("10.0.0.2"))
                .andExpect(jsonPath("$.topTargetedPaths[0].path").value("/login"))
                .andExpect(jsonPath("$.topTargetedPaths[0].count").value(2));
    }

    @Test
    void aggregatesAcrossConfigurationsWhenConfigIdIsOmitted() throws Exception {
        summary(RANGE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").doesNotExist())
                .andExpect(jsonPath("$.byCategory.DOS.count").value(1))
                .andExpect(jsonPath("$.totalEvents").isNumber());
    }

    @Test
    void emptyRangeReturnsZeroes() throws Exception {
        summary("configId=" + CONFIG + "&from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(0))
                .andExpect(jsonPath("$.byCategory").isEmpty())
                .andExpect(jsonPath("$.topAttackers").isEmpty());
    }

    @Test
    void rejectsBadParameters() throws Exception {
        summary("from=2026-06-01T00:00:00Z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
        summary("from=yesterday&to=2026-06-01T00:00:00Z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        summary("from=2026-06-02T00:00:00Z&to=2026-06-01T00:00:00Z").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"));
        summary("configId=abc&" + RANGE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("configId"));
    }
}
