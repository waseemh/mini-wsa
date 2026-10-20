package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.GeoLocation;
import com.akamai.miniwsa.model.SecurityEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class PostgresEventRepository implements EventRepository {

    private static final String INSERT = """
            INSERT INTO events (event_id, ts, received_at, config_id, policy_id, client_ip, hostname, path, method,
                status_code, user_agent, rule_id, rule_name, rule_message, severity, category, action,
                country, city, request_size, response_size, attack_type, threat_score)
            VALUES (?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING""";

    private final JdbcTemplate jdbc;

    public PostgresEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotent: an event whose eventId already exists is skipped. */
    @Override
    public void saveAll(List<EnrichedEvent> events) {
        jdbc.batchUpdate(INSERT, events, events.size(), (ps, e) -> {
            SecurityEvent ev = e.event();
            GeoLocation geo = ev.geoLocation();
            int i = 1;
            ps.setString(i++, ev.eventId());
            ps.setTimestamp(i++, Timestamp.from(ev.timestamp()));
            ps.setTimestamp(i++, Timestamp.from(e.receivedAt()));
            ps.setLong(i++, ev.configId());
            ps.setString(i++, ev.policyId());
            ps.setString(i++, ev.clientIp());
            ps.setString(i++, ev.hostname());
            ps.setString(i++, ev.path());
            ps.setString(i++, ev.method());
            ps.setObject(i++, ev.statusCode());
            ps.setString(i++, ev.userAgent());
            ps.setString(i++, ev.rule().id());
            ps.setString(i++, ev.rule().name());
            ps.setString(i++, ev.rule().message());
            ps.setString(i++, ev.rule().severity().name());
            ps.setString(i++, ev.rule().category().name());
            ps.setString(i++, ev.action().name());
            ps.setString(i++, geo == null ? null : geo.country());
            ps.setString(i++, geo == null ? null : geo.city());
            ps.setObject(i++, ev.requestSize());
            ps.setObject(i++, ev.responseSize());
            ps.setString(i++, e.attackType());
            ps.setInt(i, e.threatScore());
        });
    }
}
