# Mini WSA: Implementation Plan

Source: `Assignment.pdf` (CSI Group backend take-home, Java / Spring Boot).

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Stack | Java 21, Spring Boot 3, Maven | Required by the assignment; Maven is simplest for reviewers. |
| Root package | `com.akamai.miniwsa` | groupId `com.akamai`, artifactId `mini-wsa`. |
| Storage | PostgreSQL 16 | Simple, reliable, strong SQL for analytics, primary key gives idempotent ingestion. |
| Schema management | Flyway | Versioned migrations in `src/main/resources/db/migration`. |
| Storage access | `EventRepository` interface | `PostgresEventRepository` for production, `InMemoryEventRepository` for unit tests and quick start. |
| Tests | JUnit 5, Testcontainers (`PostgreSQLContainer`, `@ServiceConnection`) | Unit tests for enrichment; at least one integration test for the API. |
| Packaging | `Dockerfile` + `docker-compose.yml` (app + postgres with healthcheck) | Bonus item in the assignment. |

## Project structure

```
mini-wsa/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
├── PLAN.md
├── scripts/
│   └── load-sample-data.sh          # run generator, POST batches to the API
└── src/
    ├── main/
    │   ├── java/com/akamai/miniwsa/
    │   │   ├── MiniWsaApplication.java
    │   │   ├── api/                  # HTTP layer only, no logic
    │   │   │   ├── IngestController.java     # POST /v1/events/ingest
    │   │   │   ├── StatsController.java      # GET  /v1/stats/summary (+ timeseries)
    │   │   │   ├── SamplesController.java    # GET  /v1/events/samples
    │   │   │   └── error/
    │   │   │       ├── GlobalExceptionHandler.java   # @ControllerAdvice
    │   │   │       └── ApiError.java
    │   │   ├── model/
    │   │   │   ├── SecurityEvent.java        # incoming DLR (record)
    │   │   │   ├── Rule.java
    │   │   │   ├── GeoLocation.java
    │   │   │   ├── EnrichedEvent.java        # original + attackType, threatScore, receivedAt
    │   │   │   ├── Category.java             # enum + attackType label
    │   │   │   ├── Action.java               # enum
    │   │   │   ├── Severity.java             # enum + base score
    │   │   │   ├── StatsSummary.java
    │   │   │   └── SamplesPage.java
    │   │   ├── ingest/
    │   │   │   ├── EventParser.java          # single object or array -> List<SecurityEvent>
    │   │   │   ├── EventValidator.java       # required fields, enums, ISO-8601 timestamp
    │   │   │   └── IngestService.java        # validate -> enrich -> store
    │   │   ├── enrich/
    │   │   │   ├── ClassificationService.java    # category -> attackType
    │   │   │   ├── ThreatScoreService.java       # severity + action + path + repeat bonus, cap 100
    │   │   │   └── RepeatOffenderTracker.java    # sliding 10-min window per clientIp
    │   │   ├── stats/StatsService.java
    │   │   ├── samples/SamplesService.java
    │   │   ├── storage/
    │   │   │   ├── EventRepository.java              # interface
    │   │   │   ├── PostgresEventRepository.java      # JdbcTemplate, batch insert, GROUP BY queries
    │   │   │   └── InMemoryEventRepository.java
    │   │   ├── generator/
    │   │   │   ├── EventGenerator.java       # baseline traffic + attack waves, seeded RNG
    │   │   │   ├── AttackWave.java
    │   │   │   └── GeneratorRunner.java      # CLI: --count, --seed, --out / --post
    │   │   └── config/                       # only if needed (Jackson strictness, etc.)
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1__create_events.sql
    └── test/java/com/akamai/miniwsa/
        ├── enrich/
        │   ├── ClassificationServiceTest.java
        │   ├── ThreatScoreServiceTest.java       # every branch, cap, window edges
        │   └── RepeatOffenderTrackerTest.java
        ├── ingest/EventValidatorTest.java
        ├── generator/EventGeneratorTest.java     # determinism, wave shape
        └── api/PipelineIntegrationTest.java      # ingest -> summary -> samples
```

## Data flow

```
client --POST--> IngestController
                     |
                     v
                 IngestService
          EventParser -> EventValidator --(errors)--> 400 ApiError
                     |
                     v
        ClassificationService + ThreatScoreService
                     |           \-- RepeatOffenderTracker (in-memory window)
                     v
              EventRepository --> PostgreSQL (batch insert)
                     ^
GET stats / samples -+  (StatsService / SamplesService -> GROUP BY / filtered queries)
```

## Layering rules

- `api` parses HTTP and calls one service. No business logic.
- `enrich` is pure logic. The tracker takes timestamps as input and never reads the clock, so it is easy to unit test.
- `storage` is the only package that knows SQL.
- `generator` does not depend on the web layer; it produces `SecurityEvent` objects used by file output, HTTP POST mode and tests.

## Schema (Postgres)

```sql
CREATE TABLE events (
  event_id       TEXT        NOT NULL,
  ts             TIMESTAMPTZ NOT NULL,
  received_at    TIMESTAMPTZ NOT NULL,
  config_id      BIGINT      NOT NULL,
  policy_id      TEXT,
  client_ip      INET        NOT NULL,
  hostname       TEXT,
  path           TEXT        NOT NULL,
  method         TEXT,
  status_code    INT,
  user_agent     TEXT,
  rule_id        TEXT, rule_name TEXT, rule_message TEXT,
  severity       TEXT        NOT NULL,
  category       TEXT        NOT NULL,
  action         TEXT        NOT NULL,
  country        TEXT, city TEXT,
  request_size   BIGINT, response_size BIGINT,
  attack_type    TEXT        NOT NULL,
  threat_score   SMALLINT    NOT NULL,
  PRIMARY KEY (event_id)
);

CREATE INDEX idx_events_config_ts ON events (config_id, ts DESC);
CREATE INDEX idx_events_ts        ON events (ts DESC);
CREATE INDEX idx_events_ip_ts     ON events (client_ip, ts);
```

- Idempotent ingestion: `INSERT ... ON CONFLICT DO NOTHING`. Decide whether a duplicate is success or 409, and document it.
- Batch insert via `JdbcTemplate.batchUpdate` with `reWriteBatchedInserts=true`.
- Cast `?::inet` on write, `host(client_ip)` on read.

## Milestones

1. **`v0.1-ingestion`**
   - Skeleton, DTOs, enums.
   - `POST /v1/events/ingest` accepts a single object or an array.
   - Bean Validation plus timestamp format check.
   - 201 on success; 400 with per-event, per-field errors (index, `eventId`, field, message).
   - Reject the whole batch if any event is invalid (document this).
   - Server-side `receivedAt`.
   - In-memory repository.
2. **`v0.2-enrichment`**
   - `ClassificationService`: category to attackType.
   - `ThreatScoreService`: severity 40/30/20/10; action +20/+10/+0; path contains `/admin` or `/login` +15; repeat offender +15; cap 100.
   - Repeat offender: more than 5 prior events from the same `clientIp` within the last 10 minutes, measured on event `timestamp` (deterministic for replayed data). Document this choice.
   - Max natural score is 90 (40+20+15+15); keep the cap anyway.
   - Unit tests for every branch, the cap, and window edges.
   - Switch to Postgres + Flyway + docker-compose.
3. **`v0.3-stats`**
   - `GET /v1/stats/summary?configId&from&to`: total, `byCategory` (count, avgThreatScore), `byAction`, top 10 attackers (count, avgThreatScore), top 10 paths.
   - `configId` optional; aggregate across all configs when omitted.
   - One SQL statement per section; no row-pulling into Java.
   - 400 on bad params with a consistent error body.
4. **`v0.4-samples`**
   - `GET /v1/events/samples` with optional `configId`, `from`, `to`, `category`, `action`; `limit` (default 20, max 100), `offset`.
   - Sorted by timestamp descending.
   - Response: `{total, limit, offset, items}`.
   - README note: deep OFFSET is slow at scale; keyset pagination is the fix.
5. **`v0.5-generator`**
   - Seeded RNG, configurable count (e.g. 10,000).
   - Baseline traffic across several `configId`s, weighted rules, categories, geo, user agents.
   - Attack waves: bursts from one IP hitting one path in a short window.
   - Output NDJSON/JSON file or POST in batches to the API.
6. **`v0.6-timeseries`** (the one bonus)
   - `GET /v1/stats/timeseries?configId&from&to&interval={1m|5m|1h}` using `date_bin` / `date_trunc`.
7. **`v1.0`**: polish
   - Integration test, Dockerfile, global error handler.
   - README: build/run, curl examples for every endpoint, Mermaid architecture diagram, storage justification, what I'd improve, challenges, one thing I learned.

## README: storage justification (be honest about scale)

- Why Postgres: simple to run, strong SQL, transactional, primary key gives idempotency. Comfortable at assignment volume.
- Where it stops scaling: row storage makes wide aggregate scans slow, indexes cost write throughput, deep OFFSET degrades, single node.
- Path forward: monthly range partitioning on `ts` with old partitions dropped, BRIN indexes, rollup tables / materialized views, keyset pagination, read replicas, then a columnar store (ClickHouse) fed from Kafka.
- Keep the table unpartitioned now (partitioning forces `ts` into the primary key); describe it under "what I'd improve".

## Other notes

- The repeat-offender window is in memory, per instance, and resets on restart. Mention rebuilding it from the last 10 minutes of rows at startup, or moving it to Redis.
- Small, logical commits; tag each milestone.
- Email the repo link to obokobza@akamai.com a day before the interview.
- Understand every line: the interview includes live modifications.
