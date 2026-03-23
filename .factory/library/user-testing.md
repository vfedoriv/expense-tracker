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
