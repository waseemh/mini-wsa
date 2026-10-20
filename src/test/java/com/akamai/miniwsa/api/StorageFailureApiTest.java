package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import com.akamai.miniwsa.storage.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What clients see when storage fails: 503 + Retry-After when worth retrying, a leak-free 500 otherwise. */
class StorageFailureApiTest extends PostgresIntegrationTest {

    private static final String EVENT = """
            {"eventId":"f-1","timestamp":"2026-05-20T14:32:10Z","configId":1,"clientIp":"1.2.3.4","path":"/",
             "method":"GET","rule":{"id":"1","name":"R","severity":"LOW","category":"BOT"},"action":"MONITOR"}""";

    @Autowired MockMvc mvc;
    @MockBean EventRepository repository;

    @Test
    void unavailableDatabaseOnIngestIs503WithRetryAfter() throws Exception {
        when(repository.existingIds(any())).thenThrow(new DataAccessResourceFailureException("db down"));

        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(content().string(not(containsString("db down"))));
    }

    @Test
    void timeoutOnReadIs503() throws Exception {
        when(repository.topGroups(any(), any(), anyInt())).thenThrow(new QueryTimeoutException("too slow"));

        mvc.perform(get("/v1/stats/summary?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void unexpectedFailureIs500WithErrorIdAndNoInternals() throws Exception {
        when(repository.existingIds(any(Collection.class))).thenThrow(new IllegalStateException("secret internal detail"));

        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errorId").isNotEmpty())
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }
}
