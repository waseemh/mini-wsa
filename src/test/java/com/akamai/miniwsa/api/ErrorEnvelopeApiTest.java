package com.akamai.miniwsa.api;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every failure, including the ones Spring MVC raises itself, comes back in the same ApiError shape. */
class ErrorEnvelopeApiTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void unknownEndpointIs404WithEnvelope() throws Exception {
        mvc.perform(get("/v1/nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No such endpoint"));
    }

    @Test
    void wrongMethodIs405WithAllowHeader() throws Exception {
        mvc.perform(delete("/v1/events/ingest")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void invalidJsonAndEmptyBodyAreMalformedRequestsWithoutJacksonInternals() throws Exception {
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is missing or is not valid JSON"));
        mvc.perform(post("/v1/events/ingest").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void parameterErrorsNameTheFieldAndExplainTheProblem() throws Exception {
        mvc.perform(get("/v1/stats/summary?from=2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        mvc.perform(get("/v1/events/samples?category=NOPE"))
                .andExpect(jsonPath("$.errors[0].field").value("category"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.startsWith("must be one of INJECTION")));
        mvc.perform(get("/v1/events/samples?from=yesterday"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
        mvc.perform(get("/v1/events/samples?limit=abc"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_TYPE"));
        mvc.perform(get("/v1/events/samples?limit=0"))
                .andExpect(jsonPath("$.errors[0].code").value("OUT_OF_RANGE"));
        mvc.perform(get("/v1/stats/timeseries?from=2026-01-01T00:00:00Z&to=2026-01-02T00:00:00Z&interval=10m"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_ENUM"));
    }
}
