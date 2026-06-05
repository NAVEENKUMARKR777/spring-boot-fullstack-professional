# Java Backend Developer: Hiring Assignment
### Role: Java Backend Developer (1-2 years work experience)
### Time Limit: 24 hours from receipt

---

## Context

You're joining a company that builds HR technology for the construction industry. The users are site managers, HR teams, and payroll operators managing blue-collar workforce. Think daily wage workers, shift-based crews, overtime-heavy schedules.

This assignment simulates your actual job. You'll work with an existing open-source HRMS codebase, not build from scratch. This tests what matters to us: can you read someone else's code, design a solid schema, write clean APIs, and handle the backend complexity that real workforce management demands?

## Guidelines
1- You are encouraged to use AI Tools to complete this assignment.
2- You are expected to be able to explain the code and make changes to your code during the interview.
3- We care about your backend decisions. Schema design, caching strategy, data integrity. A working API with proper validation matters more than a pretty UI.

---

## Setup

Pick any open-source HRMS or Employee Management System built with **Java + Spring Boot**. Fork it. Get it running locally with the following stack:

- **Java 17+** with **Spring Boot**
- **Hibernate/JPA** for ORM
- **Supabase** as your PostgreSQL database (free tier is fine, create a project at [supabase.com](https://supabase.com))
- **Redis** for caching (local Redis or a free cloud instance)

**Suggested repo:** [SpringBoot Employee Management System](https://github.com/amigoscode/spring-boot-fullstack-professional). Spring Boot + JPA + PostgreSQL. Clean structure, good starting point.

You may use a different Java HRMS if you prefer. The tasks below work with any system that has employee/payroll functionality. If the repo you pick uses a different database, migrate it to Supabase (PostgreSQL).

---

## Part 1: Feature Build, Worker Attendance & Overtime Settlement Engine

**Scenario:** A construction company client needs to track daily attendance for site workers. Clock-in, clock-out, automatic overtime calculation, and monthly settlement reports. Site supervisors need to know who's currently on-site in real-time, and payroll needs accurate overtime numbers at month-end.

This is the core backend feature. Build the APIs, schema, caching layer, and business logic.

---

### 1. Schema Design (Hibernate Entities)

Design and implement the following entities with proper JPA relationships and constraints:

- **Worker**: name, phone, designation (enum: `MASON`, `ELECTRICIAN`, `PLUMBER`, `SUPERVISOR`, `HELPER`), daily wage rate, active status
- **Site**: site name, location, active status
- **AttendanceLog**: worker reference, site reference, clock-in timestamp, clock-out timestamp, total hours worked, overtime hours (calculated)
- **OvertimeEntry**: worker reference, attendance reference, date, overtime hours, overtime rate applied, amount, settlement status (enum: `PENDING`, `SETTLED`)

Use proper `@Table`, `@Column`, `@Index` annotations. Constraints should enforce business rules at the database level, not just in application code.

---

### 2. REST APIs

Build the following endpoints:

**Attendance**
- `POST /api/attendance/clock-in`: Log a worker's arrival at a site. Request body: `{ workerId, siteId }`
- `POST /api/attendance/clock-out`: Log a worker's departure. Request body: `{ workerId }`. System calculates total hours and overtime automatically.
- `GET /api/attendance/active`: List all workers currently clocked in across all sites. **This must be served from Redis, not the database.**
- `GET /api/attendance/log?workerId={id}&from={date}&to={date}`: Attendance history for a worker in a date range. Supports pagination.

**Overtime**
- `GET /api/overtime/summary/{workerId}?month={YYYY-MM}`: Monthly overtime summary with total overtime hours, breakdown by date, total payout amount, and settlement status.
- `POST /api/overtime/settle/{workerId}?month={YYYY-MM}`: Mark all overtime entries for that worker+month as `SETTLED`. Cannot settle the current month (only past months).

---

### 3. Redis Caching

- **Active workers cache**: When a worker clocks in, add them to a Redis hash/set with their worker ID, site info, and clock-in time. Remove on clock-out. The `GET /active` endpoint reads exclusively from Redis.
- **TTL safety net**: Set a TTL of 16 hours on each active worker entry. Nobody stays clocked in for more than 16 hours. If the clock-out was missed, the entry should expire and be flagged.
- **Cache invalidation**: When a worker's profile is updated (name, designation, wage rate), invalidate any cached entries that contain stale data.

---

### 4. Business Rules

These are the rules the system must enforce. We will test edge cases during review.

**Clock-in rules:**
- Worker must exist and be active
- Worker cannot clock in if already clocked in (no double entry)
- Clock-in time cannot be in the future
- Site must exist and be active

**Clock-out rules:**
- Worker must be currently clocked in (cannot clock out without clocking in)
- If total shift exceeds 16 hours, auto-flag the attendance record for review (add a `flagged` boolean)

**Overtime calculation:**
- Standard shift = 8 hours. Anything beyond 8 hours in a single attendance record = overtime
- Overtime rate: **1.5x** daily wage rate for the first 2 overtime hours, **2x** beyond that
- Monthly overtime cap: **60 hours** per worker. If a clock-out would push a worker past 60 hours for the month, still record the attendance but cap the overtime entry at whatever hours remain under 60

**Settlement rules:**
- Cannot settle current month, only completed months
- Once settled, overtime entries cannot be modified
- Settlement endpoint should return the total amount in the response

---

### 5. Error Handling

- All validation failures return structured JSON error responses. Not stack traces, not generic 500s.
- Use proper HTTP status codes: `400` for validation failures, `404` for not found, `409` for conflicts (duplicate clock-in, already settled)
- Error response format:
```json
{
  "error": "DUPLICATE_CLOCK_IN",
  "message": "Worker is already clocked in at Site: Greenfield Phase 2",
  "timestamp": "2026-05-25T10:30:00Z"
}
```

---

## Part 2: Ticket Blitz (5 Backend Tickets, Fast Response)

**Scenario:** You've just joined the team. It's your first week. Five tickets come in from production. These aren't single-line fixes. Each one needs you to trace a problem across multiple files: config, service, entity, repository. If you only patch the symptom in one place, it'll break again.

For each ticket: fix the issue, commit with a clear message referencing the ticket number.

---

**TICKET LF-201: API calls from frontend blocked by CORS**

"The frontend team says every API call from the React app returns a CORS error in the browser. The endpoints work fine when tested from Postman. We launched the frontend on `localhost:3000` and the backend runs on `localhost:8080`."

*What's broken:* There's no CORS configuration, or it's incomplete. This isn't a one-line fix.

*Where to look:*
- Spring Security filter chain. CORS must be processed **before** Spring Security rejects the preflight `OPTIONS` request. If security blocks the preflight, no amount of `@CrossOrigin` helps.
- A `CorsConfigurationSource` bean or `WebMvcConfigurer` implementation. Allowed origins, methods, and headers need to be explicitly registered.
- `application.yml`. Hardcoded allowed origins don't survive across environments. Externalize them as config properties so dev/staging/prod each have their own.

*Deliverable:* Frontend at `localhost:3000` can make API calls without CORS errors. Allowed origins are configurable per environment, not hardcoded.

---

**TICKET LF-202: App crashes on startup when Redis is unavailable**

"Our Redis instance went down for maintenance last night. The entire backend refused to start, threw a `RedisConnectionException` on boot and the health check API went offline. The app should still work without caching, just slower."

*What's broken:* The app treats Redis as a hard requirement. No Redis, no app. That's not acceptable.

*Where to look:*
- `application.yml`. Redis connection timeout is probably at default (infinite wait). Set a reasonable connect timeout so the app doesn't hang forever.
- `RedisConfig.java` (or wherever the Redis beans are configured). The `RedisTemplate` and `CacheManager` beans fail hard if Redis isn't reachable. They need to handle connection failure gracefully.
- You'll likely need a custom `CacheErrorHandler` (implement `org.springframework.cache.interceptor.CacheErrorHandler`). This tells Spring what to do when a cache read/write fails at runtime. Without it, a single Redis timeout mid-request kills the endpoint.
- Service layer. Check everywhere you use `@Cacheable`, `@CacheEvict`, or manual Redis calls. All of it needs to degrade to DB-only when cache is unavailable.

*Deliverable:* App starts and serves requests even when Redis is completely offline. When Redis comes back, caching resumes automatically. No manual restart needed.

---

**TICKET LF-203: Attendance history endpoint returns the entire table, and it's slow even with fewer records**

"The attendance log page takes 40+ seconds to load. I checked the API directly and `/api/attendance/log` is returning every single record in the database. We have 50,000+ attendance entries and it's dumping all of them in one response. Even when I filter to a single worker for one week (only ~6 records), it still takes 3 seconds. Something else is wrong too."

*What's broken:* The obvious problem is no pagination. The endpoint dumps the entire table because there's no limit. But pagination alone won't explain why 6 records take 3 seconds. That's the N+1 query problem: every attendance record triggers separate queries to load the associated Worker and Site entities. With 50,000 records, that's 100,000+ additional queries. Even with 6 records, it's 12 extra round-trips to the database for data that could have been loaded in one query.

*Where to look:*
- `AttendanceLog` entity. Check the `@ManyToOne` relationships to Worker and Site. If they're `EAGER`, Hibernate loads every related entity up front. If they're `LAZY`, the N+1 hits when Jackson serializes the response. Either way, individual queries per record.
- `AttendanceRepository`. The query is likely `findAll()` with no `Pageable` parameter. Add pagination, but also add an `@EntityGraph` or rewrite with `JOIN FETCH` so Worker and Site load in the same query.
- `AttendanceService` and `AttendanceController`. Pagination parameters need to flow through both layers, not just exist in the repo. Set sensible defaults (page 0, size 20).
- `application.yml`. Configure `spring.data.web.pageable.default-page-size` and `max-page-size` as a safety net.
- Response structure. Don't return a raw `List<>`. Return a wrapper with `content`, `totalElements`, `totalPages`, `currentPage`.

*Deliverable:* Endpoint returns paginated results by default with metadata. The N+1 is gone. Verify by turning on Hibernate SQL logging (`spring.jpa.show-sql=true`) and confirming the query uses joins, not separate selects per record. The old unparameterized call still works but returns only the first page.

---

**TICKET LF-204: Overtime settlement saves partial data, and workers get wrong SMS notifications**

"We settled overtime for a worker who had 22 overtime entries in March. The settlement failed on entry #15 (bad data). But entries 1-14 are already marked SETTLED in the database and the worker already received an SMS saying 'Your March overtime of ₹4,200 has been settled.' The actual amount is wrong because 7 entries are still PENDING. Now we can't re-run settlement because it skips the already-settled ones, and the worker thinks they're getting paid."

*What's broken:* The settlement logic processes entries one by one in a loop and commits each individually. When entry #15 fails, the first 14 are already committed. There's no transaction wrapping the batch. That's problem one.

Problem two is worse: the SMS notification fires **during** the settlement loop, inside the same execution path. So even if you wrap everything in a proper transaction and it rolls back on failure, the worker already got the text message. You can't un-send an SMS.

*Where to look:*
- `OvertimeService` (settlement method). How is `@Transactional` applied? The **entire settlement** for a worker+month needs to be one atomic transaction. Not per-entry, not missing entirely.
- Watch out for the Spring proxy trap. If the controller calls a non-transactional method that internally calls a transactional one on the **same bean**, the annotation is silently ignored. This is the single most common `@Transactional` mistake in Spring.
- The SMS call has to happen **after** the transaction commits, not during. If the DB rolls back, no SMS should ever have been sent. If the DB succeeds but SMS fails, the data should still be correct. Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` or a Spring event publisher. This means creating an event class, a listener class, and a publish call in the service. Not a one-file change.
- If the SMS fails after a successful settlement, log it and queue a retry. Don't crash the settlement response over a notification failure.

*Deliverable:* Settlement is all-or-nothing. All entries for a worker+month either settle together or none do. The SMS only goes out after a successful commit. If the notification fails, the settlement data is still correct. No partial state, no premature messages.

---

**TICKET LF-205: App works locally but connections exhaust on staging under moderate traffic**

"The app runs fine on my machine for hours. On the staging server (connected to Supabase), two things happen: (1) After about 30 minutes of idle time, the API returns `SQLTransientConnectionException: Connection is not available`. Restarting fixes it temporarily. (2) Even when it's not idle, if 15-20 users hit the overtime summary endpoint at the same time, the app freezes for 10+ seconds and some requests timeout. We only have 20 concurrent users on staging. This shouldn't be a problem."

*What's broken:* The idle drops are a config problem. Supabase silently kills connections that sit idle too long, but HikariCP doesn't know. It keeps handing out dead connections from the pool.

The concurrency freezes are a code problem. Look at the overtime summary service method. It runs inside `@Transactional`, and partway through, it makes a synchronous REST call to an external government API to fetch the latest minimum wage rates. That call takes 3-5 seconds. The whole time, a database connection is checked out from the pool, doing absolutely nothing. Default pool is 10 connections. 20 concurrent requests. Do the math.

*Where to look:*
- `OvertimeService` (or wherever the summary is calculated). Find the external API call sitting inside a `@Transactional` method. Fetch the external data **before** you open the transaction, then pass it in. Don't hold a DB connection hostage while you wait on someone else's API.
- `application.yml`. HikariCP defaults assume a local database that never drops connections. For Supabase you need to set `max-lifetime` shorter than the idle timeout (typically 300s), add `keepalive-time` so connections stay warm, and right-size `maximum-pool-size` for your actual traffic.
- `application-staging.yml` (or equivalent profile). These settings need to be environment-specific. Your local DB doesn't need keepalive. Staging and prod do. If there's no profile separation, create one.
- Check the Supabase connection string. It should use the **connection pooler** URL (port 6543 with PgBouncer), not the direct connection (port 5432). Getting this wrong causes connection exhaustion under load.
- The external API client (`RestTemplate` / `WebClient`) needs its own timeouts configured. A slow government API shouldn't be able to freeze your entire application.

*Deliverable:* App stays connected on staging for 24+ hours without restarts. 20 concurrent requests to the overtime summary complete without pool exhaustion. Connection pool settings are environment-specific. The external API call no longer holds a database connection. README documents the Supabase connection setup.

---

## Evaluation Criteria

| What we evaluate | What we're looking for |
|---|---|
| **Schema design** | Normalized tables, proper relationships, constraints that enforce business rules at the DB level, not just in Java code |
| **Hibernate fluency** | Correct fetch strategies, no N+1 queries, proper use of `@Transactional`, lazy vs. eager loading decisions that make sense |
| **Redis usage** | Caching the right things, not everything. Proper invalidation. Graceful degradation when Redis is down |
| **Config awareness** | Environment-specific properties, externalized secrets, connection pool tuning. Not hardcoded values that only work on your laptop |
| **Multi-layer debugging** | Tickets need tracing across config, security, service, repository. We check if you fixed the root cause or just the symptom |
| **API design** | RESTful conventions, correct HTTP status codes, consistent error responses, pagination where it matters |
| **Separation of concerns** | Transactions don't contain side effects. Network I/O doesn't hold DB connections. You know where one layer's responsibility ends and another's begins |
| **Business logic correctness** | Overtime math is right, edge cases handled (cap at 60, 16-hour flag, no double clock-in), transactions are atomic |
| **Code reading ability** | You worked within the existing codebase patterns. You didn't rewrite the project, you extended it |
| **AI usage** | We expect you to use AI tools (Claude, Copilot, etc.). Tell us which ones and how. No penalty for using AI. Penalty for not using it |

---

## The Hand-Drawn Diagram (MANDATORY)

> **In addition to the code submission, you must send a hand-drawn architecture diagram directly in the Internshala chat window.**

> Take a photo of a hand-drawn diagram that shows how you think about this system. Not just the technical architecture, but the people and the business behind it.
>
> Show us the tech: how a request flows from controller to service to repository, where Redis sits, how Hibernate connects to Supabase, where transactions start and end, when the SMS notification fires.
>
> But also show us the humans. Who actually uses this? A site supervisor standing in the sun trying to clock in 40 workers before the shift starts. A payroll operator at month-end who needs the overtime numbers to be right because real people's wages depend on it. A worker who gets an SMS saying their overtime is settled and trusts that number. How did those people shape your technical decisions? Why does the active workers cache need to be fast? Why can't settlement be half-done? Why does a wrong SMS matter more than a wrong log entry?
>
> And show us the business constraints. The 60-hour cap exists because of cost control. Settlement can't be partial because payroll downstream depends on complete data. The app has to survive Redis downtime because construction sites don't stop working when your cache goes down. Connection pooling matters because this system will serve hundreds of sites, not just your local machine.
>
> It doesn't need to be beautiful. It needs to show that you see the full picture: the people, the business, and the technology connecting them. A backend developer who only thinks in endpoints and database tables is missing the point. We want someone who understands why the code matters.

> **This is not optional. Applications without a hand-drawn diagram in the Internshala chat will not be reviewed.**

> Why we require this: most applicants use AI to generate working code without understanding the system behind it. A hand-drawn diagram is the fastest way for us to tell whether you actually thought about the problem or just forwarded AI output. It takes 15 minutes to draw if you've genuinely internalized what you built. It's impossible to fake if you haven't.

---

## Submission

1. Push your fork to a public GitHub repo
2. README must include:
   - Setup instructions (we will run it locally, include Supabase connection setup steps)
   - Which HRMS you forked and why (one line)
   - Which AI tools you used and for what
   - Any design decisions you'd call out: schema tradeoffs, caching choices, things you'd do differently with more time
3. Commits should be atomic. One per ticket (LF-201 through LF-205), separate commits for the attendance feature (schema, APIs, Redis layer, business logic)
4. Include a Postman collection or `curl` examples for all endpoints
5. **Hand-drawn architecture diagram**: photo sent in Internshala chat window
6. Send the repo link within 24 hours

---

*Assignment designed by DeepThought, May 2026*
