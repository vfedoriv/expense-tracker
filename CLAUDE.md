# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Full-stack personal expense tracker. Monorepo with `/frontend` (React + TypeScript + Vite + Tailwind CSS) and `/backend` (Java 25 + Spring Boot 4.0 + PostgreSQL 18). Deployed via Docker Compose.

## Commands

### Frontend (`/frontend`)
```bash
npm run dev       # Start Vite dev server
npm run build     # Type-check + build (tsc -b && vite build)
npm run lint      # Run ESLint
npm run preview   # Preview production build
```

### Backend (`/backend`)
```bash
./mvnw spring-boot:run           # Run application
./mvnw test                      # Run all tests
./mvnw test -Dtest=ClassName     # Run a single test class
./mvnw verify                    # Run tests + integration tests
```

### Full stack
```bash
docker compose up --build        # Build and start all services
docker compose up -d             # Start in background
```

## Architecture

### Authentication Strategy (Critical)
Auth is implemented in two phases per SPEC.md §6:
1. **Phase 1 (current)**: Fake auth — backend uses a hardcoded `FakeUser` (e.g., `admin@test.com`) as the security principal; no real OAuth needed
2. **Phase 2 (later)**: Replace `FakeUser` with real Google/GitHub SSO using Spring Security OAuth2

All business logic must be implemented before switching to real SSO. The `UserContext`/principal abstraction should be designed so SSO can be swapped in without changing service/controller logic.

### Multi-tenancy
Every category, transaction, and budget operation must be scoped to the authenticated user. No cross-user data access. Authorization enforced at the backend on every endpoint.

### WebSocket Budget Alerts
- Server pushes alerts at 50%, 80%, 100% budget usage thresholds
- Alerts fire once per threshold per month (not on every transaction change)
- Alerts trigger: on WebSocket connect, and after any transaction create/update/delete
- Client must send at least one meaningful message (e.g., subscribe, ack) that changes server behavior
- No alerts if no budget is set for current month

### Category Deletion
Deletion is **blocked** if any transactions reference the category (not reassigned). Backend returns an error; UI must handle it gracefully.

### Backend Structure (Spring Boot)
- REST API for all CRUD operations
- WebSocket endpoint for real-time alerts
- Input validation with clear error responses
- Tests use Testcontainers (PostgreSQL) and WireMock (mock OAuth providers); no real Google/GitHub network calls

### Frontend Structure (React + Vite)
- TypeScript strict mode enabled (`tsconfig.app.json`)
- ESLint 9 flat config format (`eslint.config.js`)
- Light theme only (no dark mode)
- Required screens: Login, Dashboard (monthly budget summary), Transactions (with search/filters), Categories management
- Real-time budget alerts visible in UI (toast/banner/panel)

## Key Constraints
- Backend: Java 25, Spring Boot 4.0, Maven 3.9, PostgreSQL 18
- Frontend: React 18+, Vite, Tailwind CSS, TypeScript
- Tests: JUnit + Mockito (unit), Testcontainers + WireMock (integration)
- GitHub OAuth may not provide email — identity must use `provider + provider_user_id`, not email
