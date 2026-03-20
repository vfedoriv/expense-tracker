# Personal Expense Tracker

Full-stack personal expense tracker with React frontend and Spring Boot backend. Track expenses across categories, set monthly budgets, and receive real-time alerts when spending crosses thresholds.

## Tech Stack

- **Backend**: Java 25, Spring Boot 4.0, PostgreSQL 18, Flyway
- **Frontend**: React 19, TypeScript, Vite, Tailwind CSS
- **Auth**: Google OAuth / GitHub OAuth (with fake auth for development)
- **Real-time**: WebSocket (STOMP over SockJS)
- **Tests**: JUnit 5, Mockito, Testcontainers, WireMock
- **Deployment**: Docker Compose

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 25 (for local backend development)
- Node.js 22+ (for local frontend development)

### Run with Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080

### Run Locally (Development)

**Database:**
```bash
docker compose up db -d
```

**Backend:**
```bash
cd backend
./mvnw spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` and `/ws` to `localhost:8080`.

## Running Tests

```bash
# All backend tests (requires Docker for Testcontainers)
cd backend && ./mvnw test

# Frontend type-check + build
cd frontend && npm run build

# Frontend lint
cd frontend && npm run lint
```

## Authentication

The app supports two auth modes controlled by the `AUTH_MODE` environment variable:

### Fake Auth (default, `AUTH_MODE=fake`)

Used for development. Every request is auto-authenticated as a hardcoded test user (`admin@test.com`). No OAuth credentials needed.

### Real OAuth (`AUTH_MODE=oauth2`)

#### Google OAuth Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/apis/credentials)
2. Create an OAuth 2.0 Client ID (Web application)
3. Set authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
4. Set environment variables:
   ```
   GOOGLE_CLIENT_ID=your-client-id
   GOOGLE_CLIENT_SECRET=your-client-secret
   AUTH_MODE=oauth2
   ```

#### GitHub OAuth Setup

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Create a new OAuth App
3. Set authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
4. Set environment variables:
   ```
   GITHUB_CLIENT_ID=your-client-id
   GITHUB_CLIENT_SECRET=your-client-secret
   AUTH_MODE=oauth2
   ```

**Note:** GitHub may not provide email depending on user privacy settings. Identity relies on `provider + provider_user_id`, not email. The same person signing in with Google and GitHub creates two separate accounts.

## API Reference

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/me` | Get current user info |
| POST | `/api/auth/logout` | Logout (204) |

### Categories
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/categories` | List user's categories |
| POST | `/api/categories` | Create category `{ name }` (201) |
| PUT | `/api/categories/{id}` | Rename category `{ name }` |
| DELETE | `/api/categories/{id}` | Delete category (204 or 409) |

### Transactions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/transactions` | Search/filter (paginated) |
| POST | `/api/transactions` | Create transaction (201) |
| PUT | `/api/transactions/{id}` | Update transaction |
| DELETE | `/api/transactions/{id}` | Delete transaction (204) |

**Search parameters:** `q`, `categoryId`, `dateFrom`, `dateTo`, `amountMin`, `amountMax`, `page`, `size`, `sort`

### Budgets
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/budgets/{year}/{month}` | Get budget summary |
| PUT | `/api/budgets/{year}/{month}` | Set/update budget `{ amount }` |
| DELETE | `/api/budgets/{year}/{month}` | Delete budget (204) |

## Category Deletion Behavior

Category deletion is **blocked** if any transactions reference that category. The API returns `409 Conflict` with an error message. To delete a category, first delete or reassign all its transactions. This prevents accidental data loss.

## WebSocket Budget Alerts

### Connection

Connect via STOMP over SockJS at `/ws`.

### Server → Client (Budget Alerts)

Destination: `/user/queue/budget-alerts`

```json
{
  "type": "BUDGET_ALERT",
  "threshold": 80,
  "budgetAmount": 1000.00,
  "spent": 820.50,
  "percentage": 82,
  "year": 2026,
  "month": 3,
  "message": "You have reached 80% of your monthly budget"
}
```

**Alert rules:**
- Thresholds: 50%, 80%, 100%
- Alerts fire **once per threshold per month** (no spam on repeated edits)
- Alerts are generated on WebSocket connect and after any transaction create/update/delete
- No alerts if no budget is set for the current month
- Acknowledged alerts are not re-sent on reconnect

### Client → Server (Acknowledge)

Destination: `/app/alerts/ack`

```json
{
  "threshold": 80
}
```

Acknowledging an alert marks it as read. Acknowledged alerts are not re-sent when the client reconnects.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_DB` | `expense_tracker` | Database name |
| `POSTGRES_USER` | `expense_user` | Database user |
| `POSTGRES_PASSWORD` | `expense_pass` | Database password |
| `AUTH_MODE` | `fake` | Auth mode: `fake` or `oauth2` |
| `GOOGLE_CLIENT_ID` | — | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | — | Google OAuth client secret |
| `GITHUB_CLIENT_ID` | — | GitHub OAuth client ID |
| `GITHUB_CLIENT_SECRET` | — | GitHub OAuth client secret |
