package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requests are bounded, so no single client can exhaust memory or the few pooled database connections. */
class ResourceLimitsApiTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private ResultActions ingest(String body) throws Exception {
        return mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void batchLargerThanTheLimitIs413() throws Exception {
        ingest("[" + String.join(",", Collections.nCopies(5001, "{}")) + "]")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at most 5000")));
    }

    @Test
    void batchAtTheLimitIsProcessedNormally() throws Exception {
        ingest("[" + String.join(",", Collections.nCopies(5000, "{}")) + "]")
                .andExpect(status().isBadRequest())   // 400 because the events are empty, not 413
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void bodyLargerThanTheLimitIs413WithoutBeingFullyParsed() throws Exception {
        String hugeUserAgent = "a".repeat(9 * 1024 * 1024);
        ingest("{\"userAgent\":\"" + hugeUserAgent + "\"}")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("Request body is larger than 8 MB"));
    }

    @Test
    void statementsHaveATimeout() {
        assertThat(jdbc.getQueryTimeout()).isEqualTo(10);
    }
}
