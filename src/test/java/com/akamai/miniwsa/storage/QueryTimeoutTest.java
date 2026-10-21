package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** A runaway statement is cancelled by the database and surfaces as an exception the API turns into a 503. */
@TestPropertySource(properties = "spring.jdbc.template.query-timeout=1s")
class QueryTimeoutTest extends PostgresIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void slowStatementIsCancelledAndMapsToAnExceptionTheApiHandles() {
        long start = System.nanoTime();

        Throwable thrown = catchThrowable(() -> jdbc.execute("SELECT pg_sleep(8)"));

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        // GlobalExceptionHandler answers both of these with 503 + Retry-After
        assertThat(thrown).isInstanceOfAny(QueryTimeoutException.class, DataAccessResourceFailureException.class);
    }
}
