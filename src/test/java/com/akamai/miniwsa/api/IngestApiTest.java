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
    void rejectsInvalidEnumWithAllowedValues() throws Exception {
        ingest(event("e-1", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("rule.severity"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of CRITICAL, HIGH, MEDIUM, LOW"))
                .andExpect(jsonPath("$.errors[0].index").doesNotExist());   // single event, not a batch
    }

    @Test
    void rejectsInvalidTimestamp() throws Exception {
        ingest(event("t-1", "HIGH").replace("2026-05-20T14:32:10Z", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timestamp"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("ISO 8601")));
    }

    @Test
    void reportsEveryProblemAcrossABatch() throws Exception {
        String badEnum = event("m-1", "NOPE");
        String badTimestamp = event("m-2", "HIGH").replace("2026-05-20T14:32:10Z", "yesterday");
        String missingPath = event("m-3", "HIGH").replace("\"path\":\"/api/v1/login\",", "");
        String negativeStatus = event("m-4", "HIGH").replace("403", "42");

        ingest("[" + String.join(",", badEnum, event("m-ok", "LOW"), badTimestamp, missingPath, negativeStatus) + "]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.index==0 && @.field=='rule.severity' && @.code=='INVALID_ENUM')]").exists())
                .andExpect(jsonPath("$.errors[?(@.index==2 && @.field=='timestamp' && @.code=='INVALID_FORMAT')]").exists())
                .andExpect(jsonPath("$.errors[?(@.index==3 && @.field=='path' && @.code=='REQUIRED')]").exists())
                .andExpect(jsonPath("$.errors[?(@.index==4 && @.field=='statusCode' && @.code=='OUT_OF_RANGE')]").exists())
                .andExpect(jsonPath("$.errors[?(@.index==1)]").isEmpty())
                .andExpect(jsonPath("$.truncated").doesNotExist());
    }

    @Test
    void capsTheErrorListOfAHugeBadBatch() throws Exception {
        String body = "[" + String.join(",", java.util.Collections.nCopies(300, "{}")) + "]";
        ingest(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(100))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void rejectsBodyThatIsNeitherEventNorArray() throws Exception {
        ingest("\"hello\"").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
        ingest("[1, null]").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void rejectsBatchAtomically() throws Exception {
        ingest("[" + event("atomic-1", "HIGH") + ",{\"eventId\":\"bad\"}]").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE event_id = 'atomic-1'", Integer.class)).isZero();
    }

    @Test
    void duplicateEventIdIsIgnored() throws Exception {
        ingest(event("dup-1", "HIGH")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(1)).andExpect(jsonPath("$.duplicates").value(0));
        ingest(event("dup-1", "HIGH")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted").value(0)).andExpect(jsonPath("$.duplicates").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE event_id = 'dup-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsClientIpThatIsNotAnIpAddress() throws Exception {
        ingest(event("ip-1", "HIGH").replace("203.0.113.42", "not-an-ip"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("clientIp"));
        ingest(event("ip-2", "HIGH").replace("203.0.113.42", "999.1.1.1")).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsIpv6() throws Exception {
        ingest(event("ip-3", "HIGH").replace("203.0.113.42", "2001:db8::1")).andExpect(status().isCreated());
    }

    @Test
    void rejectsOutOfRangeStatusCode() throws Exception {
        ingest(event("sc-1", "HIGH").replace("403", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("statusCode"));
    }

    @Test
    void rejectsEpochNumberAsTimestamp() throws Exception {
        ingest(event("ty-1", "HIGH").replace("\"2026-05-20T14:32:10Z\"", "1716215530"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("timestamp"));
    }

    @Test
    void rejectsDecimalWhereWholeNumberExpected() throws Exception {
        ingest(event("dec-1", "HIGH").replace("14227", "14227.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("configId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
    }

    @Test
    void rejectsNumberGivenAsString() throws Exception {
        ingest(event("ty-2", "HIGH").replace("14227", "\"14227\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("configId"));
    }
}
