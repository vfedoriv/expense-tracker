# Expense Tracker

A full-stack personal expense tracking application with real-time budget alerts.

**Stack:** Java 25 · Spring Boot 4.0 · PostgreSQL · React 19 · Vite · Tailwind CSS v4

---

## Running locally

### Prerequisites

- Docker (for PostgreSQL)
- Java 25+, Maven 3.9+
- Node.js 22+

### 1. Start PostgreSQL

```bash
docker compose up postgres -d
```

### 2. Configure environment variables (optional)

A single `.env` file at the project root is read by both the backend and the frontend:

```bash
cp .env.example .env
# edit .env as needed
```

Both services pick it up automatically at startup — no `export` commands needed. If the file is absent, both services start in **fake auth mode** with default local database settings — no configuration needed for development.

### 3. Run the backend

```bash
cd backend
mvn spring-boot:run
```

Backend starts at `http://localhost:8080`.

By default, **fake authentication** is active (`APP_AUTH_FAKE=true`). The frontend sends an `X-User-Email` header on every request; the backend creates or looks up a user by that email automatically. No OAuth credentials are required in this mode.

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts at `http://localhost:5173` with API proxied to `localhost:8080`.

---

## Running with Docker Compose (all services)

```bash
docker compose up --build
```

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`

---

## Running tests

### Backend

```bash
cd backend
mvn test
```

Requires Docker (Testcontainers starts a PostgreSQL container per test class).

### Frontend

```bash
cd frontend
npm test          # run once
npm run test:watch  # watch mode
```

---

## API overview

All endpoints are prefixed with `/api` and require authentication.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users/me` | Current user profile |
| GET/POST | `/api/categories` | List / create categories |
| PUT/DELETE | `/api/categories/{id}` | Rename / delete a category |
| GET/POST | `/api/transactions` | List (with filters) / create transactions |
| PUT/DELETE | `/api/transactions/{id}` | Update / delete a transaction |
| GET/PUT | `/api/budgets/{year}/{month}` | Get or set monthly budget |

**Transaction filter query params:** `search`, `categoryId`, `dateFrom`, `dateTo`, `amountMin`, `amountMax`

---

## Category deletion behavior

Deletion is **blocked** if the category has transactions. The API returns `409 Conflict` with a descriptive message. The user must reassign or delete those transactions first before the category can be removed.

This was chosen over silent reassignment to prevent accidental data corruption and to make the user aware of the impact before proceeding.

---

## WebSocket budget alerts

### Connection

Connect via SockJS at `/ws`, with `X-User-Email` header (fake auth mode) or session cookie (OAuth2 mode):

```js
const client = new Client({
  webSocketFactory: () => new SockJS('/ws'),
  connectHeaders: { 'X-User-Email': 'you@example.com' },
})
```

### Client → Server message (Subscribe)

After connecting, the client sends a subscribe message to trigger an immediate threshold check for the given month:

```
Destination: /app/budget-alerts/subscribe
Body:
{
  "month": "2026-03"   // ISO year-month string
}
```

This affects server behavior: the server checks the current budget usage for that month and pushes any not-yet-fired threshold alerts immediately.

### Server → Client alerts

Subscribe to:

```
/user/topic/budget-alerts
```

Alert payload:

```json
{
  "threshold": 80,
  "spent": 800.00,
  "budget": 1000.00,
  "message": "You have used 80% of your March 2026 budget"
}
```

### Alert rules

- Thresholds: **50%**, **80%**, **100%** of the monthly budget
- Each threshold fires **at most once** per user per calendar month (even if spending drops below the threshold after a delete)
- Alerts are sent:
  - When the client sends a subscribe message
  - After a transaction is created, updated, or deleted
- If no budget is set for the current month, no alerts are generated

---

## Configuring Google OAuth

1. Create a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Enable the **Google Identity** API and create an **OAuth 2.0 Client ID** (Web application).
3. Add authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
4. Add to `.env` in the project root:

```
APP_AUTH_FAKE=false
VITE_FAKE_AUTH=false
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<your-client-id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<your-client-secret>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,profile,email
```

---

## Configuring GitHub OAuth

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**.
2. Set Homepage URL to `http://localhost:8080` and Authorization callback URL to `http://localhost:8080/login/oauth2/code/github`.
3. Add to `.env` in the project root:

```
APP_AUTH_FAKE=false
VITE_FAKE_AUTH=false
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID=<your-client-id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET=<your-client-secret>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_SCOPE=user:email,read:user
```

> **Note:** GitHub may not provide an email address depending on user privacy settings. The app uses `provider + provider_user_id` as the primary identity key, so accounts always work even without an email.

---

## Environment variables reference

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_AUTH_FAKE` | `true` | Use fake header-based auth instead of OAuth2 |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `expensetracker` | Database name |
| `DB_USERNAME` | `expensetracker` | Database user |
| `DB_PASSWORD` | `expensetracker` | Database password |
| `SERVER_PORT` | `8080` | Backend HTTP port |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | — | Google OAuth client ID |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | — | Google OAuth client secret |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_ID` | — | GitHub OAuth client ID |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GITHUB_CLIENT_SECRET` | — | GitHub OAuth client secret |
