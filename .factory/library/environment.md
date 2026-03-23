# Environment

Environment variables, external dependencies, and setup notes.

**What belongs here:** Required env vars, external API keys/services, dependency quirks, platform-specific notes.
**What does NOT belong here:** Service ports/commands (use `.factory/services.yaml`).

---

## Java / Maven
- Java 25 (OpenJDK Zulu 25.0.2) via SDKMAN
- Maven 3.8.8
- Spring Boot 4.0.4 requires new modular starters (e.g., `spring-boot-starter-webmvc` instead of `spring-boot-starter-web`)
- Use `@MockitoBean` instead of deprecated `@MockBean` (Spring Boot 4.0)
- `@SpringBootTest` no longer auto-configures MockMVC; add `@AutoConfigureMockMvc` explicitly
- `@AutoConfigureMockMvc` is in package `org.springframework.boot.webmvc.test.autoconfigure` (NOT `org.springframework.boot.test.autoconfigure.web.servlet`)
- Flyway requires `flyway-database-postgresql` dependency for PostgreSQL 18 support (community edition)
- JVM timezone `Europe/Kiev` is rejected by PostgreSQL 18 (renamed to `Europe/Kyiv`). Set `-Duser.timezone=UTC` in surefire and spring-boot-maven-plugin
- Docker Compose volume for PostgreSQL 18 must be `/var/lib/postgresql` (NOT `/var/lib/postgresql/data`) due to PG18 directory structure change
- Rancher Desktop has intermittent port-forwarding issues with Testcontainers; tests use Docker Compose PostgreSQL on localhost:5432 instead
- For `@SpringBootTest` with security-test, use `spring-boot-starter-security-test` (not raw `spring-security-test`)

## Node.js / Frontend
- Node.js 25.8.1, npm 11.11.0
- React 19, Vite 8, TypeScript 5.9
- Tailwind CSS needs to be installed and configured (not in scaffold)

## Database
- PostgreSQL 18 via Docker Compose on port 5432
- Database name: expense_tracker
- Database user: expense_tracker / password: expense_tracker
- Flyway for schema migrations

## OAuth (Milestone 5)
- Google OAuth: requires GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET env vars
- GitHub OAuth: requires GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET env vars
- Credentials deferred to milestone 5 (fake auth until then)
