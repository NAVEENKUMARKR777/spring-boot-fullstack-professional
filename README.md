# Spring Boot HRMS — Worker Attendance & Overtime Settlement Engine

[![Build](https://github.com/amigoscode/spring-boot-fullstack-professional/actions/workflows/build.yml/badge.svg)](https://github.com/amigoscode/spring-boot-fullstack-professional/actions/workflows/build.yml)

---

## Forked From

**[amigoscode/spring-boot-fullstack-professional](https://github.com/amigoscode/spring-boot-fullstack-professional)**

Chosen because it uses Spring Boot + JPA + PostgreSQL with a clean layered structure (controller → service → repository), making it easy to extend without rewriting — exactly what the assignment asked for.

---

## Setup Instructions

### Prerequisites

- Java 17+
- Maven 3.8+
- A [Supabase](https://supabase.com) project (free tier)
- Redis (local `redis-server` or free cloud instance e.g. [Redis Cloud](https://redis.com/try-free/))

### 1. Clone the repo

```bash
git clone https://github.com/NAVEENKUMARKR777/spring-boot-fullstack-professional.git
cd spring-boot-fullstack-professional
```

### 2. Set up Supabase

1. Create a new project at [supabase.com](https://supabase.com)
2. Go to **Settings → Database**
3. Copy the **Connection Pooler** URL (port `6543` with PgBouncer — **not** the direct port 5432)
4. The URL format is:
   ```
   jdbc:postgresql://db.<PROJECT_REF>.supabase.co:6543/postgres?pgbouncer=true&sslmode=require
   ```

> **Important:** Always use the PgBouncer pooler URL (port 6543) in staging/prod. The direct connection (port 5432) has a 60-connection cap and will exhaust under load.

### 3. Configure environment variables

```bash
export DB_URL="jdbc:postgresql://db.<ref>.supabase.co:6543/postgres?pgbouncer=true&sslmode=require"
export DB_USERNAME="postgres"
export DB_PASSWORD="<your-supabase-db-password>"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export CORS_ORIGINS="http://localhost:3000"
```

Or copy `.env.example` to `.env` and fill in values if using a dotenv loader.

### 4. Start Redis locally

```bash
redis-server
```

> **The app runs fine without Redis** (LF-202 fix) — caching degrades gracefully and the active-workers endpoint returns an empty list.

### 5. Run the application

```bash
./mvnw spring-boot:run -P '!build-frontend'
```

The API will be available at `http://localhost:8080`.

To build with the React frontend:

```bash
./mvnw clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### 6. Staging profile (Supabase)

```bash
java -jar target/demo-0.0.1-SNAPSHOT.jar --spring.profiles.active=staging \
  -DSUPABASE_DB_URL="jdbc:postgresql://..." \
  -DSUPABASE_DB_USERNAME="postgres" \
  -DSUPABASE_DB_PASSWORD="..." \
  -DREDIS_HOST="..." \
  -DREDIS_PASSWORD="..."
```

---

## API Reference

### Workers
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/workers` | List all active workers |
| POST | `/api/workers` | Create worker |
| PUT | `/api/workers/{id}` | Update worker (invalidates Redis cache) |
| DELETE | `/api/workers/{id}` | Deactivate worker |

### Sites
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/sites` | List all active sites |
| POST | `/api/sites` | Create site |

### Attendance
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/attendance/clock-in` | `{ workerId, siteId }` |
| POST | `/api/attendance/clock-out` | `{ workerId }` |
| GET | `/api/attendance/active` | Active workers from Redis cache |
| GET | `/api/attendance/log?workerId=1&from=2026-05-01&to=2026-05-31&page=0&size=20` | Paginated history |

### Overtime
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/overtime/summary/{workerId}?month=2026-05` | Monthly overtime summary |
| POST | `/api/overtime/settle/{workerId}?month=2026-05` | Settle all pending entries atomically |

### Error response shape

```json
{
  "error": "DUPLICATE_CLOCK_IN",
  "message": "Worker is already clocked in at Site: Greenfield Phase 2",
  "timestamp": "2026-05-25T10:30:00Z"
}
```

---

## curl / Postman Examples

```bash
# Create a worker
curl -X POST http://localhost:8080/api/workers \
  -H 'Content-Type: application/json' \
  -d '{"name":"Raju Singh","phone":"9876543210","designation":"MASON","dailyWageRate":800}'

# Create a site
curl -X POST http://localhost:8080/api/sites \
  -H 'Content-Type: application/json' \
  -d '{"name":"Greenfield Phase 2","location":"Sector 45, Noida"}'

# Clock in
curl -X POST http://localhost:8080/api/attendance/clock-in \
  -H 'Content-Type: application/json' \
  -d '{"workerId":1,"siteId":1}'

# Clock out
curl -X POST http://localhost:8080/api/attendance/clock-out \
  -H 'Content-Type: application/json' \
  -d '{"workerId":1}'

# Active workers (from Redis)
curl http://localhost:8080/api/attendance/active

# Attendance history (paginated)
curl "http://localhost:8080/api/attendance/log?workerId=1&from=2026-05-01&to=2026-05-31"

# Overtime summary
curl "http://localhost:8080/api/overtime/summary/1?month=2026-05"

# Settle overtime
curl -X POST "http://localhost:8080/api/overtime/settle/1?month=2026-05"
```

---

## Ticket Fixes (Part 2)

### LF-201 — CORS blocked by Spring Security

**Root cause:** No `CorsConfigurationSource` bean registered in the Spring Security filter chain. `@CrossOrigin` on controllers is useless when Spring Security rejects the preflight `OPTIONS` request before the request reaches any controller.

**Fix:**
- `CorsConfig.java`: `CorsConfigurationSource` bean reading `app.cors.allowed-origins` from properties (not hardcoded)
- `SecurityConfig.java`: `.cors(cors -> cors.configurationSource(...))` — wires CORS into the Security filter chain so it runs before auth checks
- `application.properties`: `app.cors.allowed-origins=${CORS_ORIGINS:http://localhost:3000}` — overridable per environment

### LF-202 — App crashes on startup when Redis is unavailable

**Root cause:** Default Lettuce connect timeout is effectively infinite. Any `@Cacheable` exception propagates and kills the request. Redis was treated as a hard dependency.

**Fix:**
- `RedisConfig.java`: 2-second connect + command timeout via `LettuceClientConfiguration`
- `ResilientCacheErrorHandler.java`: implements `CacheErrorHandler` — logs cache failures instead of throwing, so `@Cacheable` falls through to DB
- `management.health.redis.enabled=false` — prevents Redis downtime from showing the app as unhealthy
- All manual `RedisTemplate` calls in `AttendanceService` wrapped in `try-catch` with warn-log fallback

### LF-203 — Attendance endpoint dumps full table, slow even for 6 records

**Root cause (1):** No pagination — `findAll()` with no `Pageable`.
**Root cause (2):** N+1 queries — `@ManyToOne` on Worker and Site both `LAZY`; Jackson serializes them, triggering one SELECT per record.

**Fix:**
- `AttendanceRepository.findHistoryByWorker`: uses `@EntityGraph({"worker","site"})` to JOIN FETCH both relations in one query
- Separate `countQuery` required because Hibernate cannot derive COUNT from a JOIN FETCH query
- `PageResponse<T>` wrapper: `content`, `totalElements`, `totalPages`, `currentPage`
- Old unpaginated calls still work — default to page 0, size 20
- `spring.data.web.pageable.max-page-size=100` as a safety net

### LF-204 — Settlement saves partial data, wrong SMS sent

**Root cause (1):** Settlement iterated entries in a loop, committing each individually. Entry #15 fails, #1–14 already committed.
**Root cause (2):** SMS fired during the settlement loop — before the transaction completed. Even a rollback can't un-send an SMS.

**Fix:**
- `OvertimeService.settle()` is `@Transactional` — all 22 entries settle in one transaction or none do
- **Spring proxy trap avoided:** `settle()` is `public` and called from `OvertimeController` (a different Spring bean), so the proxy intercepts correctly
- SMS moved to `SettlementEventListener` with `@TransactionalEventListener(phase = AFTER_COMMIT)` — fires only after the DB transaction commits successfully
- SMS failure is caught and logged; it does not roll back or affect settlement correctness

### LF-205 — Connections exhausted on staging under moderate traffic

**Root cause (1):** HikariCP defaults (`max-lifetime` = 30min) larger than Supabase's idle timeout (~5min) → dead connections handed out from pool.
**Root cause (2):** `getOvertimeSummary()` was `@Transactional`. Inside, it made a synchronous call to the government minimum-wage API (3–5s). For those 5s, a DB connection sat idle in the pool. With 10 connections and 20 users, pool exhausted in seconds.

**Fix:**
- `OvertimeService.getSummary()` is **not** `@Transactional`. External API call happens first (no DB connection held), then DB reads follow as short auto-commit queries
- `application-staging.properties`: `max-lifetime=240000` (4min < Supabase's 5min idle timeout), `keepalive-time=60000`, `connection-test-query=SELECT 1`
- Supabase URL uses PgBouncer connection pooler (port 6543), not direct connection (port 5432)
- `AppConfig.java`: RestTemplate with 5s connect + read timeout — a slow government API cannot freeze the app

---

## Design Decisions

### Schema: SEQUENCE generators with `allocationSize = 1`

Supabase uses PostgreSQL sequences. With the default `allocationSize = 50`, Hibernate over-allocates IDs and can skip large gaps when the app restarts. `allocationSize = 1` ensures IDs match the DB sequence exactly.

### Redis active-workers structure

Used individual string keys `active_worker:{workerId}` (JSON, TTL 16h) + a set `active_workers` for membership. Alternatives considered:
- **Redis Hash:** Can't set per-field TTL — rejected (can't enforce 16h per worker)
- **Sorted set by clockInTime:** Cleaner for time-ordered queries, but GET active doesn't need ordering

### Monthly overtime cap (60h): still record attendance, cap the entry

Per the assignment: "still record the attendance but cap the overtime entry." So the `AttendanceLog` gets the raw totalHours, while `OvertimeEntry` gets capped hours. This lets payroll see the full shift while respecting the cap.

### OvertimeEntry: one entry per attendance, effective rate field

Rather than two entries per attendance (one at 1.5x, one at 2x), a single entry stores the total amount and a `overtimeRateApplied` field indicating the highest tier reached (1.5 or 2.0). This keeps settlement queries simple (one record per shift) while the exact tier breakdown is derivable from the amount.

### Things I'd do differently with more time

1. **Idempotency keys on clock-in/out** — prevent duplicate submissions from mobile apps in poor connectivity
2. **Redis keyspace notifications** for missed clock-out flagging instead of the hourly `@Scheduled` job
3. **Outbox pattern for SMS** — persist the notification in a DB table, process asynchronously with retry logic; currently a failed SMS is just logged
4. **Integration tests** with Testcontainers (PostgreSQL + Redis) rather than unit tests with mocks
5. **Audit columns** (`createdAt`, `updatedAt`, `createdBy`) on all entities using `@EntityListeners(AuditingEntityListener.class)`

---

## AI Tools Used

- **Claude (claude.ai/code — Claude Sonnet 4.6)**: Architecture planning, all code generation, ticket root-cause analysis, schema design, and Redis/transaction pattern decisions. Used extensively as described in the assignment guidelines.
- Every piece of code was reviewed and the logic understood before committing — I can explain and modify any part during the interview.
