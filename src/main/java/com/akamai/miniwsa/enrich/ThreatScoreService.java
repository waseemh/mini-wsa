package com.akamai.miniwsa.enrich;

import com.akamai.miniwsa.model.SecurityEvent;
import org.springframework.stereotype.Service;

/** Threat score 0-100: severity + action + sensitive path + repeat-offender bonus. */
@Service
public class ThreatScoreService {

    static final int MAX_SCORE = 100;
    static final int SENSITIVE_PATH_BONUS = 15;
    static final int REPEAT_OFFENDER_BONUS = 15;
    static final int REPEAT_OFFENDER_THRESHOLD = 5;

    /** @param recentEventsFromIp events already seen from the same IP in the last 10 minutes */
    public int score(SecurityEvent event, int recentEventsFromIp) {
        int score = event.rule().severity().score() + event.action().score();
        if (event.path().contains("/admin") || event.path().contains("/login")) {
            score += SENSITIVE_PATH_BONUS;
        }
        if (recentEventsFromIp > REPEAT_OFFENDER_THRESHOLD) {
            score += REPEAT_OFFENDER_BONUS;
        }
        return Math.min(score, MAX_SCORE);
    }
}
