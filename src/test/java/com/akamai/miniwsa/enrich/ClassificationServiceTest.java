package com.akamai.miniwsa.enrich;

import com.akamai.miniwsa.TestEvents;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClassificationServiceTest {

    private final ClassificationService service = new ClassificationService();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            INJECTION          | SQL/Command Injection
            XSS                | Cross-Site Scripting
            PROTOCOL_VIOLATION | Protocol Anomaly
            DATA_LEAKAGE       | Data Exfiltration
            BOT                | Bot Activity
            DOS                | Denial of Service
            RATE_LIMIT         | Rate Limiting
            """)
    void mapsCategoryToAttackType(Category category, String expected) {
        var event = TestEvents.event("e", "1.1.1.1", Instant.now(), category, Severity.LOW, Action.MONITOR, "/");
        assertThat(service.attackType(event)).isEqualTo(expected);
    }

    @Test
    void coversEveryCategory() {
        assertThat(Category.values()).hasSize(7);
    }
}
