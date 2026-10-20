package com.akamai.miniwsa.enrich;

import com.akamai.miniwsa.TestEvents;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatScoreServiceTest {

    private final ThreatScoreService service = new ThreatScoreService();

    @ParameterizedTest
    @CsvSource({
            "CRITICAL, MONITOR, 40", "HIGH, MONITOR, 30", "MEDIUM, MONITOR, 20", "LOW, MONITOR, 10",
            "LOW, ALERT, 20", "LOW, DENY, 30", "CRITICAL, DENY, 60"})
    void severityPlusAction(Severity severity, Action action, int expected) {
        assertThat(service.score(TestEvents.event(severity, action, "/home"), 0)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"/admin, 25", "/api/v1/login, 25", "/admin/users, 25", "/home, 10"})
    void sensitivePathAddsFifteen(String path, int expected) {
        assertThat(service.score(TestEvents.event(Severity.LOW, Action.MONITOR, path), 0)).isEqualTo(expected);
    }

    @Test
    void repeatOffenderNeedsMoreThanFiveRecentEvents() {
        var event = TestEvents.event(Severity.LOW, Action.MONITOR, "/home");
        assertThat(service.score(event, 5)).isEqualTo(10);
        assertThat(service.score(event, 6)).isEqualTo(25);
    }

    @Test
    void allBonusesStack() {
        var event = TestEvents.event(Severity.CRITICAL, Action.DENY, "/admin");
        assertThat(service.score(event, 10)).isEqualTo(90);
    }

    @Test
    void neverExceedsMax() {
        // 40 + 20 + 15 + 15 = 90 is the natural maximum, so the cap only matters if weights change
        var event = TestEvents.event(Severity.CRITICAL, Action.DENY, "/admin");
        assertThat(service.score(event, 100)).isLessThanOrEqualTo(ThreatScoreService.MAX_SCORE);
    }
}
