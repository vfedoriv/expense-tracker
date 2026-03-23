# User Testing

Testing surface, required testing skills/tools, and resource cost classification.

**What belongs here:** How to test the app manually, what tools to use, resource constraints.

---

## Validation Surface

- **Primary surface:** Web browser (React frontend on http://localhost:5173)
- **API surface:** REST endpoints on http://localhost:8080/api/*
- **WebSocket surface:** STOMP over WebSocket on ws://localhost:8080/ws

## Required Testing Skills/Tools

- **agent-browser**: For all UI validation (navigate pages, fill forms, click buttons, take screenshots, verify visual states)
- **curl**: For direct API testing (authorization checks, validation errors, cross-user isolation)

Practical note: `agent-browser` network output may omit HTTP status codes; use `curl` (or explicit in-page request logging) when status-code evidence is required by assertions.

## Setup for Validation

1. Ensure PostgreSQL is running: `docker compose up -d postgres`
2. Start backend: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`
3. Start frontend: `cd frontend && npm run dev -- --port 5173`
4. Frontend accessible at http://localhost:5173
5. Backend API at http://localhost:8080

## Fake Auth (Milestones 1-4)

During milestones 1-4, a fake auth filter auto-authenticates all requests as a hardcoded user.
To test multi-user isolation, the fake auth should support switching users via a header or query parameter.

## Validation Concurrency

- **Machine:** 16 GB RAM, 10 CPU cores
- **Baseline usage:** ~12 GB (moderate pressure)
- **Per agent-browser instance:** ~300 MB (lightweight app)
- **Services footprint:** PostgreSQL ~256 MB, Backend ~512 MB, Frontend ~256 MB = ~1 GB
- **Available headroom:** ~3 GB * 0.7 = ~2.1 GB
- **Max concurrent validators:** 2-3
- **Rationale:** With moderate existing memory pressure and services running, 2-3 concurrent agent-browser validators is safe. More may cause swapping.

## Flow Validator Guidance: Web Browser

- Use only the assigned app URL and credentials/isolation context provided by the parent validator.
- Stay within project ports only: `5173` (frontend), `8080` (backend), `5432` (postgres).
- Do not alter global machine state or unrelated services/processes.
- Keep evidence scoped to your assigned group directory under mission evidence.
- For this project surface, avoid changing seed data outside the assigned fake user context unless explicitly instructed.

## Flow Validator Guidance: API

- Use only `http://localhost:8080/api/*` with explicit `X-User-Id` headers for isolation checks.
- Do not use destructive cleanup against global tables; create scoped test data with unique prefixes per flow.
- Keep all requests within project ports (`8080` only for API checks) and never call external services.
- Record exact request/response status codes and payload snippets for each assertion under test.
- Save command transcripts and JSON responses only under the assigned mission evidence directory.
