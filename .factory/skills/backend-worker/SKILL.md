---
name: backend-worker
description: Implements backend features — Spring Boot 4.0 REST APIs, JPA entities, services, Flyway migrations, and tests
---

# Backend Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Backend-only features: REST API endpoints, JPA entities, repository layer, service layer, Flyway migrations, Spring Security configuration, WebSocket handlers, backend tests.

## Required Skills

None.

## Work Procedure

1. **Read context**: Read `mission.md` for database schema and architecture. Read `.factory/library/architecture.md` and `.factory/library/environment.md` for conventions and env details. Read `.factory/services.yaml` for commands.

2. **Understand the feature**: Read the feature description, preconditions, expectedBehavior, and verificationSteps thoroughly. Identify all acceptance criteria.

3. **Write tests FIRST (TDD red phase)**:
   - Write failing unit tests (JUnit 5 + Mockito) for service layer logic
   - Write failing integration tests (Testcontainers + `@SpringBootTest`) for API endpoints
   - Use `@AutoConfigureMockMvc` with `@SpringBootTest` (Spring Boot 4.0 requirement)
   - Use `@MockitoBean` instead of deprecated `@MockBean`
   - Run `cd backend && mvn test` — confirm tests fail for the right reasons

4. **Implement to make tests pass (TDD green phase)**:
   - Write Flyway migration SQL if new tables/columns are needed
   - Create/update JPA entities matching the schema in `mission.md`
   - Implement repository interfaces (Spring Data JPA)
   - Implement service classes with business logic
   - Implement REST controllers with proper request/response DTOs
   - Add validation annotations (`@NotBlank`, `@Positive`, etc.)
   - Add exception handling in `@ControllerAdvice`

5. **Run all tests**: `cd backend && mvn test` — ALL tests must pass (not just new ones)

6. **Manual verification**:
   - Start the backend: ensure PostgreSQL is running, then `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
   - Test key endpoints with curl (at least 2-3 representative calls)
   - Verify error responses for invalid inputs
   - Stop the backend process when done

7. **Update shared state**: If you discover patterns, quirks, or important knowledge, update `.factory/library/architecture.md` or `.factory/library/environment.md`.

### Key Spring Boot 4.0 Conventions
- Starter names changed: use `spring-boot-starter-webmvc` (not `spring-boot-starter-web`)
- Security OAuth client: `spring-boot-starter-security-oauth2-client`
- WebSocket: `spring-boot-starter-websocket`
- Test: `spring-boot-starter-webmvc-test` (for MockMvc support)
- Package root: `com.expensetracker`
- Use separate `year` (SMALLINT) and `month` (SMALLINT) columns for budget-related tables, NOT a combined year_month string

## Example Handoff

```json
{
  "salientSummary": "Implemented Category CRUD REST API with endpoints POST/PUT/DELETE/GET /api/categories. Added Flyway migration V2 for categories table. Service enforces unique name per user and blocks deletion when transactions exist. Ran mvn test (12 passing) and verified with curl: POST returns 201, duplicate name returns 409, DELETE with transactions returns 409.",
  "whatWasImplemented": "Category REST API: POST /api/categories (create), PUT /api/categories/{id} (rename), DELETE /api/categories/{id} (delete with block check), GET /api/categories (list). CategoryService with business logic for uniqueness and deletion blocking. CategoryRepository with Spring Data JPA. Flyway V2__create_categories.sql. CategoryRequest/CategoryResponse DTOs. Exception handling for duplicate names (409) and not found (404).",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      {"command": "cd backend && mvn test", "exitCode": 0, "observation": "12 tests passed, 0 failed"},
      {"command": "curl -s -X POST http://localhost:8080/api/categories -H 'Content-Type: application/json' -d '{\"name\":\"Food\"}' -w '%{http_code}'", "exitCode": 0, "observation": "201 with category response body"},
      {"command": "curl -s -X POST http://localhost:8080/api/categories -H 'Content-Type: application/json' -d '{\"name\":\"Food\"}' -w '%{http_code}'", "exitCode": 0, "observation": "409 duplicate name error"}
    ],
    "interactiveChecks": []
  },
  "tests": {
    "added": [
      {"file": "src/test/java/com/expensetracker/service/CategoryServiceTest.java", "cases": [
        {"name": "createCategory_success", "verifies": "creates category and returns it"},
        {"name": "createCategory_duplicateName_throwsConflict", "verifies": "rejects duplicate name per user"},
        {"name": "deleteCategory_withTransactions_throwsConflict", "verifies": "blocks deletion when transactions exist"},
        {"name": "deleteCategory_noTransactions_success", "verifies": "allows deletion when no transactions"}
      ]},
      {"file": "src/test/java/com/expensetracker/controller/CategoryControllerTest.java", "cases": [
        {"name": "POST_categories_201", "verifies": "creates category via API"},
        {"name": "POST_categories_409_duplicate", "verifies": "409 for duplicate name"},
        {"name": "GET_categories_returns_only_own", "verifies": "authorization isolation"},
        {"name": "DELETE_categories_409_has_transactions", "verifies": "blocks deletion via API"}
      ]}
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- Feature requires frontend changes (wrong worker type)
- Database schema in mission.md is ambiguous or contradictory
- Existing tests fail before starting work (pre-existing issue)
- Cannot resolve Maven dependency or Spring Boot 4.0 compatibility issue
