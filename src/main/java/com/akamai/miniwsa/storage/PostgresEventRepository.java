package com.akamai.miniwsa.storage;

import com.akamai.miniwsa.model.BucketInterval;
import com.akamai.miniwsa.model.EnrichedEvent;
import com.akamai.miniwsa.model.EventFilter;
import com.akamai.miniwsa.model.GeoLocation;
import com.akamai.miniwsa.model.Rule;
import com.akamai.miniwsa.model.SamplesPage;
import com.akamai.miniwsa.model.SecurityEvent;
import com.akamai.miniwsa.model.StatsGroup;
import com.akamai.miniwsa.model.Action;
import com.akamai.miniwsa.model.Category;
import com.akamai.miniwsa.model.Severity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

@Repository
public class PostgresEventRepository implements EventRepository {

    private static final String INSERT = """
            INSERT INTO events (event_id, ts, received_at, config_id, policy_id, client_ip, hostname, path, method,
                status_code, user_agent, rule_id, rule_name, rule_message, severity, category, action,
                country, city, request_size, response_size, attack_type, threat_score)
            VALUES (?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING""";

    private static final String SELECT_EVENT = """
            SELECT event_id, ts, received_at, config_id, policy_id, host(client_ip) AS client_ip, hostname, path,
                method, status_code, user_agent, rule_id, rule_name, rule_message, severity, category, action,
                country, city, request_size, response_size, attack_type, threat_score
            FROM events""";

    /** A SQL WHERE clause (empty if there is nothing to filter on) and its bind values. */
    private record Where(String sql, List<Object> args) {

        static Where of(EventFilter f) {
            List<String> conditions = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            if (f.configId() != null) {
                conditions.add("config_id = ?");
                args.add(f.configId());
            }
            if (f.from() != null) {
                conditions.add("ts >= ?");
                args.add(Timestamp.from(f.from()));
            }
            if (f.to() != null) {
                conditions.add("ts < ?");
                args.add(Timestamp.from(f.to()));
            }
            if (f.category() != null) {
                conditions.add("category = ?");
                args.add(f.category().name());
            }
            if (f.action() != null) {
                conditions.add("action = ?");
                args.add(f.action().name());
            }
            return new Where(conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions), args);
        }
    }

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

    @Override
    public List<StatsGroup> topGroups(Dimension dimension, EventFilter filter, int limit) {
        Where where = Where.of(filter);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        String sql = "SELECT " + dimension.sql + " AS key, count(*) AS cnt, "
                + "round(avg(threat_score), 1) AS avg_score FROM events" + where.sql()
                + " GROUP BY 1 ORDER BY cnt DESC, key LIMIT ?";
        return jdbc.query(sql, (rs, i) -> new StatsGroup(rs.getString("key"), rs.getLong("cnt"),
                rs.getDouble("avg_score")), args.toArray());
    }

    @Override
    public SamplesPage samples(EventFilter filter, int limit, int offset) {
        Where where = Where.of(filter);
        Long total = jdbc.queryForObject("SELECT count(*) FROM events" + where.sql(), Long.class,
                where.args().toArray());

        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        // event_id breaks timestamp ties so pages never overlap
        List<EnrichedEvent> items = jdbc.query(
                SELECT_EVENT + where.sql() + " ORDER BY ts DESC, event_id LIMIT ? OFFSET ?",
                PostgresEventRepository::mapEvent, args.toArray());
        return new SamplesPage(total, limit, offset, items);
    }

    @Override
    public Map<Instant, Long> countsPerBucket(EventFilter filter, BucketInterval interval) {
        Where where = Where.of(filter);
        List<Object> args = new ArrayList<>();
        args.add(interval.seconds() + " seconds");
        args.addAll(where.args());
        Map<Instant, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT date_bin(?::interval, ts, TIMESTAMPTZ '1970-01-01 00:00:00+00') AS bucket, count(*) AS cnt "
                        + "FROM events" + where.sql() + " GROUP BY 1 ORDER BY 1",
                rs -> {
                    counts.put(rs.getTimestamp("bucket").toInstant(), rs.getLong("cnt"));
                }, args.toArray());
        return counts;
    }

    private static EnrichedEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        Rule rule = new Rule(rs.getString("rule_id"), rs.getString("rule_name"), rs.getString("rule_message"),
                Severity.valueOf(rs.getString("severity")), Category.valueOf(rs.getString("category")));
        String country = rs.getString("country");
        String city = rs.getString("city");
        GeoLocation geo = country == null && city == null ? null : new GeoLocation(country, city);

        SecurityEvent event = new SecurityEvent(rs.getString("event_id"), rs.getTimestamp("ts").toInstant(),
                rs.getLong("config_id"), rs.getString("policy_id"), rs.getString("client_ip"),
                rs.getString("hostname"), rs.getString("path"), rs.getString("method"),
                rs.getObject("status_code", Integer.class), rs.getString("user_agent"), rule,
                Action.valueOf(rs.getString("action")), geo,
                rs.getObject("request_size", Long.class), rs.getObject("response_size", Long.class));
        return new EnrichedEvent(event, rs.getTimestamp("received_at").toInstant(), rs.getString("attack_type"),
                rs.getInt("threat_score"));
    }
}
