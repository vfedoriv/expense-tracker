# Architecture

Architectural decisions, patterns, and conventions discovered during the mission.

**What belongs here:** Patterns, conventions, architectural decisions, code organization notes.

---

## Backend Architecture
- Spring Boot 4.0.4 with layered architecture: controller → service → repository
- Package: `com.expensetracker`
- DTOs separate from entities (request/response DTOs in `dto/` package)
- Global exception handler via `@ControllerAdvice`
- Fake auth filter injects a hardcoded user into SecurityContext (milestones 1-4), reads X-User-Id header
- WebSocket via STOMP for budget alerts
- WebSocket auth identity is established during the SockJS/HTTP handshake (via `WebSocketAuthInterceptor` reading `X-User-Id`), not from STOMP `CONNECT` headers; frontend `connectHeaders` do not affect backend handshake user resolution.
- `@AutoConfigureMockMvc` import from `org.springframework.boot.webmvc.test.autoconfigure` (Spring Boot 4.0 package change)
- Integration tests use Docker Compose PostgreSQL on localhost:5432 (not Testcontainers) due to Rancher Desktop quirk
- JVM arg `-Duser.timezone=UTC` set in maven-surefire-plugin and spring-boot-maven-plugin
- **Jackson 3.x** in Spring Boot 4.0: ObjectMapper is `tools.jackson.databind.ObjectMapper` (NOT `com.fasterxml.jackson.databind.ObjectMapper`). Use `com.jayway.jsonpath.JsonPath` for test response parsing or Jackson 3.x imports.
- **Hibernate 7 + PG18 TEXT columns:** TEXT columns mapped as bytea, causing LOWER() failures in JPQL. Use native SQL queries with `::text` cast for LOWER/LIKE on TEXT columns, or add `@JdbcTypeCode(SqlTypes.VARCHAR)` to entity fields.
- **Integration test cleanup:** Use `@BeforeEach` cleanup in integration tests to avoid 409 conflicts on re-runs. Pattern: DELETE rows matching test data prefix before each test.
- **Spring Framework 7 method parameter validation:** `@RequestParam`/`@PathVariable` constraint failures can raise `HandlerMethodValidationException` (not just `MethodArgumentNotValidException`), so `@ControllerAdvice` handlers should extract violations from `getParameterValidationResults()` to return clean 400 responses.

## Frontend Architecture
- React 19 with TypeScript
- Vite 8 as build tool
- Tailwind CSS for styling (light theme only)
- React Router for navigation
- Custom hooks for data fetching (useCategories, useTransactions, useBudget, etc.)
- API client with auth headers (Axios or fetch wrapper)
- Context for auth state
- Backend `LocalDate` values (`YYYY-MM-DD`) must be formatted with timezone-safe parsing (split components and build `new Date(year, monthIndex, day)`); avoid `new Date(dateString)` because it is UTC-based and can shift the displayed calendar day.

## Database
- PostgreSQL 18 with Flyway migrations
- Separate `year` (SMALLINT) and `month` (SMALLINT) columns for monthly_budgets and budget_alert_states
- All tables have created_at and updated_at timestamps
- User isolation via user_id FK on all data tables
