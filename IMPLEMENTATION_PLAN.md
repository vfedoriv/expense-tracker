# Implementation Plan: Personal Expense Tracker

## Context

Build a full-stack personal expense tracker per SPEC.md requirements. The project is a greenfield monorepo with only scaffolding: an empty Maven skeleton (`backend/pom.xml` with just GAV), a fresh Vite+React template (`frontend/`), and no root-level infrastructure (no docker-compose, .gitignore, README). Every file needs to be created from scratch.

Key architectural decision from SPEC.md §6: implement fake auth first, build all business logic, then swap to real Google/GitHub SSO later.

---

## Phase 1: Project Infrastructure

### Root-level files
- **`.gitignore`** — IDE files, target/, node_modules/, dist/, .env
- **`.env.example`** — POSTGRES_DB/USER/PASSWORD, DATABASE_URL, GOOGLE/GITHUB_CLIENT_ID/SECRET, AUTH_MODE
- **`docker-compose.yml`** — Services: `db` (postgres:18), `backend` (builds from backend/Dockerfile), `frontend` (builds from frontend/Dockerfile)

### Backend setup
- **`backend/pom.xml`** — Rewrite entirely. Spring Boot 4.0 parent. Dependencies: spring-boot-starter-web, data-jpa, validation, websocket, security, oauth2-client, actuator, postgresql, flyway-core + flyway-database-postgresql, lombok. Test: spring-boot-starter-test, testcontainers-postgresql, junit-jupiter, wiremock-standalone, spring-security-test
- **Maven wrapper** — Generate `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` (Maven 3.9.x)
- **`backend/src/main/resources/application.yml`** — Datasource, JPA (ddl-auto: validate, open-in-view: false), Flyway, OAuth2 client registrations, `app.auth.mode: ${AUTH_MODE:fake}`
- **`backend/src/main/resources/application-test.yml`** — Testcontainers JDBC URL, auth mode: fake
- **`backend/Dockerfile`** — Multi-stage: eclipse-temurin:25-jdk build, eclipse-temurin:25-jre runtime

### Frontend setup
- Install: `@tailwindcss/vite tailwindcss react-router-dom @stomp/stompjs sockjs-client` + dev types
- **`frontend/vite.config.ts`** — Add tailwindcss plugin, proxy `/api` and `/ws` to localhost:8080
- **`frontend/src/index.css`** — Replace with `@import "tailwindcss";`
- **`frontend/index.html`** — Change title to "Expense Tracker"
- Delete: `App.css`, template assets (hero.png, react.svg, vite.svg)
- **`frontend/Dockerfile`** — Multi-stage: node:22-alpine build, nginx:alpine serve
- **`frontend/nginx.conf`** — Proxy /api and /ws to backend, SPA fallback

---

## Phase 2: Backend Core (Fake Auth)

### Database schema — `backend/src/main/resources/db/migration/V1__init_schema.sql`

**`users`**
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PRIMARY KEY |
| provider | VARCHAR(20) | NOT NULL — 'google', 'github', 'fake' |
| provider_user_id | VARCHAR(255) | NOT NULL |
| email | VARCHAR(255) | nullable |
| display_name | VARCHAR(255) | NOT NULL |
| avatar_url | VARCHAR(512) | nullable |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| | | UNIQUE(provider, provider_user_id) |

**`categories`**
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PRIMARY KEY |
| user_id | BIGINT | NOT NULL, FK → users(id) |
| name | VARCHAR(100) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| | | UNIQUE(user_id, name) |

**`transactions`**
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PRIMARY KEY |
| user_id | BIGINT | NOT NULL, FK → users(id) |
| category_id | BIGINT | NOT NULL, FK → categories(id) |
| title | VARCHAR(255) | NOT NULL |
| amount | NUMERIC(12,2) | NOT NULL, CHECK(amount > 0) |
| currency | VARCHAR(3) | NOT NULL, DEFAULT 'USD' |
| transaction_date | DATE | NOT NULL |
| notes | TEXT | nullable |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| | | INDEX(user_id, transaction_date) |
| | | INDEX(user_id, category_id) |

**`monthly_budgets`**
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PRIMARY KEY |
| user_id | BIGINT | NOT NULL, FK → users(id) |
| year | INT | NOT NULL |
| month | INT | NOT NULL, CHECK(month BETWEEN 1 AND 12) |
| amount | NUMERIC(12,2) | NOT NULL, CHECK(amount > 0) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| | | UNIQUE(user_id, year, month) |

**`budget_alert_log`**
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGSERIAL | PRIMARY KEY |
| user_id | BIGINT | NOT NULL, FK → users(id) |
| year | INT | NOT NULL |
| month | INT | NOT NULL |
| threshold | INT | NOT NULL — 50, 80, or 100 |
| alerted_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |
| acknowledged | BOOLEAN | NOT NULL DEFAULT FALSE |
| | | UNIQUE(user_id, year, month, threshold) |

### Java package: `io.github.vfedoriv.expensetracker`

**Auth layer** (designed for seamless swap to SSO):
- `auth/AppUser.java` — Record: id, provider, providerId, email, displayName
- `auth/UserContext.java` — Interface: `AppUser getCurrentUser()`
- `auth/FakeAuthFilter.java` — `OncePerRequestFilter`, creates/looks up fake user, sets SecurityContext
- `auth/FakeUserContext.java` — Reads from SecurityContext
- `auth/AuthController.java` — `GET /api/auth/me`, `POST /api/auth/logout`
- `config/SecurityConfig.java` — Conditional on `app.auth.mode`. Fake mode: disable CSRF, permit auth endpoints, require auth for /api/**, add FakeAuthFilter

**Domain modules** (each has Entity, Repository, Service, Controller, DTOs):

| Module | Endpoints | Key Logic |
|--------|-----------|-----------|
| `category/` | `GET/POST /api/categories`, `PUT/DELETE /api/categories/{id}` | Unique name per user; block delete if transactions exist (409 Conflict) |
| `transaction/` | `GET /api/transactions` (paginated, search/filter), `POST/PUT/DELETE /api/transactions/{id}` | JPA Specification for search (q, categoryId, dateFrom/To, amountMin/Max); validate category belongs to user; publish `TransactionChangedEvent` on CUD |
| `budget/` | `GET/PUT/DELETE /api/budgets/{year}/{month}` | Upsert budget; summary returns budgetSet boolean; "No budget" = budgetSet:false with null amounts |

**Shared**:
- `exception/GlobalExceptionHandler.java` — @RestControllerAdvice: validation errors (400), not found (404), deletion blocked / duplicate name (409), generic (500)
- `exception/ErrorResponse.java` — Record with timestamp, status, error, message, path, fieldErrors[]
- DTOs are Java records. Controllers never expose JPA entities.

### REST API Summary

```
GET    /api/auth/me                          → UserResponse
POST   /api/auth/logout                      → 204

GET    /api/categories                       → CategoryResponse[]
POST   /api/categories                       → CategoryResponse (201)
PUT    /api/categories/{id}                  → CategoryResponse
DELETE /api/categories/{id}                  → 204 (or 409 if has transactions)

GET    /api/transactions?q=&categoryId=&dateFrom=&dateTo=&amountMin=&amountMax=&page=&size=&sort=
                                             → Page<TransactionResponse>
POST   /api/transactions                     → TransactionResponse (201)
PUT    /api/transactions/{id}                → TransactionResponse
DELETE /api/transactions/{id}                → 204

GET    /api/budgets/{year}/{month}            → BudgetSummaryResponse
PUT    /api/budgets/{year}/{month}            → BudgetSummaryResponse
DELETE /api/budgets/{year}/{month}            → 204
```

---

## Phase 3: Frontend Core

### Architecture
- **State**: React Context + hooks (no Redux). `AuthContext` for user state, custom hooks (`useCategories`, `useTransactions`, `useBudget`) for data fetching
- **API client**: `src/api/client.ts` — thin fetch wrapper; auto-redirect to login on 401
- **Routing**: react-router-dom. `/login` (public), `/` (dashboard), `/transactions`, `/categories` (all protected)
- **Styling**: Tailwind CSS v4 via `@tailwindcss/vite` plugin. Light theme only.

### File structure
```
src/
  api/          client.ts, auth.ts, categories.ts, transactions.ts, budgets.ts
  types/        index.ts (User, Category, Transaction, BudgetSummary, Page<T>, etc.)
  context/      AuthContext.tsx, WebSocketContext.tsx
  hooks/        useCategories.ts, useTransactions.ts, useBudget.ts, useDebounce.ts
  components/
    layout/     AppLayout.tsx, Navbar.tsx, Sidebar.tsx
    ui/         Button, Input, Select, Modal, LoadingSpinner, EmptyState, Pagination
  pages/        LoginPage, DashboardPage, TransactionsPage, CategoriesPage
  features/
    transactions/  TransactionList, TransactionFilters, TransactionForm, TransactionCard
    categories/    CategoryList, CategoryForm
    budget/        BudgetSummary, BudgetSetForm, BudgetProgressBar
    alerts/        AlertToast, AlertContainer
```

### Pages
- **LoginPage**: Centered card, "Continue with Google" / "Continue with GitHub" buttons (fake auth: both call GET /api/auth/me)
- **DashboardPage**: Month selector, budget summary card (amount/spent/remaining/percentage/progress bar), "No budget set" empty state, recent transactions mini-list
- **TransactionsPage**: Search input + filter controls (category, date range, amount range), add button, paginated table (cards on mobile), create/edit modal with client-side validation
- **CategoriesPage**: Category list with rename/delete, add form, delete confirmation, error on blocked deletion

### Responsive strategy
- Table → cards on mobile for transactions
- Sidebar → hamburger menu on mobile
- Tailwind default breakpoints (sm/md/lg)

---

## Phase 4: WebSocket Budget Alerts

### Backend
- **`config/WebSocketConfig.java`** — STOMP over SockJS at `/ws`. Broker: `/queue`, app prefix: `/app`, user prefix: `/user`
- **`alert/BudgetAlertService.java`** — Core logic:
  1. Get current year + month, find budget (if none → no alerts)
  2. Sum transactions for that month, calculate percentage
  3. For each threshold [50, 80, 100]: if percentage >= threshold AND no existing `budget_alert_log` row → insert log + send STOMP message to `/user/queue/budget-alerts`
  4. Acknowledged alerts not re-sent on reconnect
- **Triggered by**: `@EventListener` on `TransactionChangedEvent` (published by TransactionService) and `@SubscribeMapping` on connect
- **`alert/BudgetAlertWebSocketHandler.java`** — `@SubscribeMapping("/queue/budget-alerts")` calls checkAndSendAlerts; `@MessageMapping("/alerts/ack")` marks alert acknowledged

### Frontend
- **`context/WebSocketContext.tsx`** — STOMP client via @stomp/stompjs + sockjs-client. Subscribes to `/user/queue/budget-alerts`, sends ack to `/app/alerts/ack`
- **`features/alerts/AlertContainer.tsx`** — Fixed top-right toast stack. Yellow/orange/red by threshold. Auto-dismiss after 8s, sends ack on dismiss

### WebSocket message format
- **Server → Client**: `{ type: "BUDGET_ALERT", threshold: 50, budgetAmount: 1000, spent: 520, percentage: 52, year: 2026, month: 3, message: "..." }`
- **Client → Server (ack)**: `{ threshold: 50 }` to `/app/alerts/ack` — changes server behavior (acknowledged alerts not resent)

---

## Phase 5: Testing

### Test infrastructure
- `TestcontainersConfig.java` — PostgreSQL 18 container with `@ServiceConnection`
- `application-test.yml` — Testcontainers JDBC URL

### Required tests (per SPEC.md §5)

| Test | Type | Covers |
|------|------|--------|
| `FakeAuthFilterTest` | Unit | SSO login success path (fake mode) |
| `CategoryServiceTest` | Unit (Mockito) | Create, delete (blocked + success), rename, duplicate name |
| `TransactionServiceTest` | Unit (Mockito) | Create, update, delete, search, invalid category |
| `BudgetServiceTest` | Unit | Get summary (with/without budget), set, delete |
| `BudgetAlertServiceTest` | Unit | No budget → no alerts, 50%/80%/100% threshold crossing, already-alerted → no repeat, ack |
| `CategoryControllerIntegrationTest` | Integration (Testcontainers) | Full CRUD via MockMvc |
| `TransactionControllerIntegrationTest` | Integration | CRUD + search/filter + validation errors |
| `BudgetControllerIntegrationTest` | Integration | Set/get/delete budget |
| `CrossUserAccessTest` | Integration | User B cannot access User A's categories/transactions/budgets |
| `BudgetAlertWebSocketIntegrationTest` | Integration | STOMP connect → create transactions → receive alerts at thresholds |

---

## Phase 6: Real SSO Authentication

- **`auth/CustomOAuth2UserService.java`** + **`CustomOidcUserService.java`** — Extract provider + provider_user_id + profile data, create/update local User entity
- **`auth/OAuth2UserContext.java`** — Implements UserContext reading from OAuth2AuthenticationToken
- **`auth/OAuth2AuthenticationSuccessHandler.java`** — Redirect to frontend after login
- **SecurityConfig** updated for `app.auth.mode=oauth2`: enable OAuth2 login, configure redirect URIs
- **Frontend LoginPage** changes: buttons navigate to `/api/oauth2/authorization/google` and `/api/oauth2/authorization/github`
- **`OAuthLoginIntegrationTest`** — WireMock stubs for Google/GitHub token + userinfo endpoints

### Identity resolution
- `provider + provider_user_id` as unique identity (NOT email)
- Same person with Google and GitHub = two separate accounts (per SPEC)

---

## Phase 7: Docker & Documentation

- Finalize **`docker-compose.yml`** with health checks and env vars
- Write **`README.md`**: run instructions, test instructions, API reference, category deletion behavior (blocked), OAuth config (Google + GitHub), WebSocket message format and alert rules
- Remove `db/init.sql` if Flyway handles everything

---

## Verification

1. **Backend builds**: `cd backend && ./mvnw clean package` (in Docker or with Java 25)
2. **Backend tests pass**: `cd backend && ./mvnw test` (requires Docker for Testcontainers)
3. **Frontend builds**: `cd frontend && npm run build` (no TS errors)
4. **Frontend lint**: `cd frontend && npm run lint` (no ESLint errors)
5. **Full stack via Docker**: `docker compose up --build` → frontend at :3000, backend at :8080
6. **End-to-end flow**: Login → create categories → create transactions → set budget → see dashboard → receive WebSocket alerts when spending crosses thresholds → search/filter transactions
7. **Cross-user isolation**: Log in as different user → cannot see other user's data

---

## Key Decisions (User-confirmed)

- **Stack versions**: Spring Boot 4.0 + Java 25 (both GA and available).
- **Lombok**: Yes — use @Data, @Builder, @NoArgsConstructor on JPA entities. Records for DTOs.
- **Month representation**: Separate `year INT` + `month INT` columns in `monthly_budgets` and `budget_alert_log` tables.
- **Workflow**: Incremental commits per phase. Each phase committed separately so progress is checkpointed and testable.
