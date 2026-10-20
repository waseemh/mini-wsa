CREATE TABLE events (
    event_id      TEXT PRIMARY KEY,
    ts            TIMESTAMPTZ NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL,
    config_id     BIGINT      NOT NULL,
    policy_id     TEXT,
    client_ip     INET        NOT NULL,
    hostname      TEXT,
    path          TEXT        NOT NULL,
    method        TEXT        NOT NULL,
    status_code   INT,
    user_agent    TEXT,
    rule_id       TEXT        NOT NULL,
    rule_name     TEXT        NOT NULL,
    rule_message  TEXT,
    severity      TEXT        NOT NULL,
    category      TEXT        NOT NULL,
    action        TEXT        NOT NULL,
    country       TEXT,
    city          TEXT,
    request_size  BIGINT,
    response_size BIGINT,
    attack_type   TEXT        NOT NULL,
    threat_score  SMALLINT    NOT NULL
);

CREATE INDEX idx_events_config_ts ON events (config_id, ts DESC);
CREATE INDEX idx_events_ts        ON events (ts DESC);
CREATE INDEX idx_events_ip_ts     ON events (client_ip, ts);
