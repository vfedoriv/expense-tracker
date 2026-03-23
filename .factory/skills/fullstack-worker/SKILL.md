---
name: fullstack-worker
description: Implements features spanning both backend and frontend — scaffolding, auth, WebSocket, Docker, and integrated features
---

# Fullstack Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Features that require coordinated backend + frontend work: project scaffolding, authentication flow, WebSocket integration, Docker Compose setup, README documentation, and features where splitting backend/frontend would create too much context switching.

## Required Skills

- **agent-browser**: For manual verification of the full-stack integrated feature. Invoke after implementation to verify end-to-end flows work (e.g., login flow, WebSocket alerts appearing in UI).

## Work Procedure

1. **Read context**: Read `mission.md` for full architecture (both backend and frontend sections, database schema). Read `.factory/library/` files. Read `.factory/services.yaml` for commands.

2. **Understand the feature**: Read the feature description, preconditions, expectedBehavior, and verificationSteps. Identify backend and frontend work.

3. **Plan the work**: Break the feature into backend-first, then frontend steps. Backend provides the API; frontend consumes it.

4. **Backend implementation (TDD)**:
   - Write failing backend tests first
   - Implement backend code (migrations, entities, services, controllers)
   - Run `cd backend && mvn test` — all pass

5. **Frontend implementation (TDD)**:
   - Write failing frontend tests first
   - Implement React components, hooks, pages
   - Use Tailwind CSS for styling
   - Run `cd frontend && npm test` — all pass
   - Run `cd frontend && npm run lint` — clean

6. **Integration verification**:
   - Start all services (PostgreSQL, backend, frontend)
   - Use agent-browser to test the end-to-end flow
   - Verify both happy path and error cases
   - Each verified flow = one `interactiveChecks` entry

7. **Run full verification suite**:
   - `cd backend && mvn test` — all pass
   - `cd frontend && npm test` — all pass
   - `cd frontend && npm run lint` — clean

8. **Update shared state**: Update `.factory/library/` files with any discoveries.

### Key Conventions
- Spring Boot 4.0 modular starters (see backend-worker skill for details)
- Use `@MockitoBean` not `@MockBean`
- Tailwind CSS for all frontend styling
- Separate `year`/`month` columns (SMALLINT) for budget tables
- Fake auth via filter (milestones 1-4), real OAuth2 (milestone 5)
- WebSocket via STOMP protocol

## Example Handoff

```json
{
  "salientSummary": "Built the complete project foundation: Spring Boot 4.0 backend with all dependencies, Flyway migrations for all 5 tables, fake auth filter, health endpoint, CORS config. React frontend with Tailwind CSS, React Router, layout shell, auth context, API client. Docker Compose with PostgreSQL 18. Verified end-to-end: frontend loads, API responds, fake auth works.",
  "whatWasImplemented": "Backend: pom.xml with Spring Boot 4.0 parent and all starters, application.yml with profiles, Flyway V1-V5 migrations (users, categories, transactions, monthly_budgets, budget_alert_states), all JPA entities, FakeAuthFilter, SecurityConfig with CORS, HealthController. Frontend: Tailwind CSS config, React Router setup with protected routes, Layout component with header/nav, AuthContext, apiClient with base URL and interceptors, placeholder pages. Docker: docker-compose.yml with PostgreSQL 18.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      {"command": "cd backend && mvn test", "exitCode": 0, "observation": "5 tests passed (health check, context loads, entity mappings)"},
      {"command": "cd frontend && npm test", "exitCode": 0, "observation": "3 tests passed (App renders, routing works)"},
      {"command": "cd frontend && npm run lint", "exitCode": 0, "observation": "clean"},
      {"command": "curl -s http://localhost:8080/api/health", "exitCode": 0, "observation": "200 OK {\"status\":\"UP\"}"}
    ],
    "interactiveChecks": [
      {"action": "Opened http://localhost:5173 in browser", "observed": "App loaded with layout, navigation visible, dashboard page shown (empty state)"},
      {"action": "Checked API communication", "observed": "Frontend fetched from backend without CORS errors, fake auth header present"}
    ]
  },
  "tests": {
    "added": [
      {"file": "backend/src/test/java/com/expensetracker/ExpenseTrackerApplicationTest.java", "cases": [
        {"name": "contextLoads", "verifies": "Spring context starts successfully"}
      ]}
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- Docker Compose configuration conflicts with existing containers
- Spring Boot 4.0 dependency resolution fails
- Cannot establish communication between frontend and backend (CORS, proxy issues)
- OAuth provider configuration issues (milestone 5)
