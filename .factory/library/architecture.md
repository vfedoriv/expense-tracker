# Architecture

Architectural decisions, patterns, and conventions discovered during the mission.

**What belongs here:** Patterns, conventions, architectural decisions, code organization notes.

---

## Backend Architecture
- Spring Boot 4.0 with layered architecture: controller → service → repository
- Package: `com.expensetracker`
- DTOs separate from entities (request/response DTOs in `dto/` package)
- Global exception handler via `@ControllerAdvice`
- Fake auth filter injects a hardcoded user into SecurityContext (milestones 1-4)
- WebSocket via STOMP for budget alerts

## Frontend Architecture
- React 19 with TypeScript
- Vite 8 as build tool
- Tailwind CSS for styling (light theme only)
- React Router for navigation
- Custom hooks for data fetching (useCategories, useTransactions, useBudget, etc.)
- API client with auth headers (Axios or fetch wrapper)
- Context for auth state

## Database
- PostgreSQL 18 with Flyway migrations
- Separate `year` (SMALLINT) and `month` (SMALLINT) columns for monthly_budgets and budget_alert_states
- All tables have created_at and updated_at timestamps
- User isolation via user_id FK on all data tables
