package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IngestApiTest extends PostgresIntegrationTest {

    private static final String EVENT = """
            {"eventId":"%s","timestamp":"2026-05-20T14:32:10Z","configId":14227,"policyId":"pol_web1",
             "clientIp":"203.0.113.42","hostname":"www.example.com","path":"/api/v1/login","method":"POST",
             "statusCode":403,"userAgent":"Mozilla/5.0",
             "rule":{"id":"950001","name":"SQL_INJECTION","message":"SQL Injection Attack Detected",
                     "severity":"%s","category":"INJECTION"},
             "action":"DENY","geoLocation":{"country":"CN","city":"Beijing"},
             "requestSize":1024,"responseSize":256}""";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private String event(String id, String severity) {
        return EVENT.formatted(id, severity);
    }

    private org.springframework.test.web.servlet.ResultActions ingest(String body) throws Exception {
        return mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void acceptsSingleEvent() throws Exception {
        ingest(event("single-1", "CRITICAL")).andExpect(status().isCreated()).andExpect(jsonPath("$.accepted").value(1));

        var row = jdbc.queryForMap("SELECT * FROM events WHERE event_id = 'single-1'");
        assertThat(row.get("attack_type")).isEqualTo("SQL/Command Injection");
        assertThat(((Number) row.get("threat_score")).intValue()).isEqualTo(75);   // 40 + 20 (DENY) + 15 (/login)
        assertThat(row.get("received_at")).isNotNull();
        assertThat(row.get("client_ip").toString()).isEqualTo("203.0.113.42");
    }

    @Test
    void acceptsBatch() throws Exception {
        ingest("[" + event("b-1", "HIGH") + "," + event("b-2", "LOW") + "]")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accepted").value(2));
    }

    @Test
    void rejectsMissingRequiredFieldWithDetails() throws Exception {
        ingest("[" + event("ok-1", "HIGH") + ",{\"eventId\":\"bad\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.index==1 && @.field=='path')]").exists());
    }

    @Test
    void rejectsInvalidEnum() throws Exception {
        ingest(event("e-1", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("rule.severity"));
    }

    @Test
    void rejectsInvalidTimestamp() throws Exception {
        ingest(event("t-1", "HIGH").replace("2026-05-20T14:32:10Z", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timestamp"));
    }

    @Test
    void rejectsBatchAtomically() throws Exception {
        ingest("[" + event("atomic-1", "HIGH") + ",{\"eventId\":\"bad\"}]").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE event_id = 'atomic-1'", Integer.class)).isZero();
    }

    @Test
    void duplicateEventIdIsIgnored() throws Exception {
        ingest(event("dup-1", "HIGH")).andExpect(status().isCreated());
        ingest(event("dup-1", "HIGH")).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE event_id = 'dup-1'", Integer.class)).isEqualTo(1);
    }
}
