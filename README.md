# Personal Expense Tracker

A full-stack web application for tracking personal expenses across categories, managing monthly budgets, and receiving real-time budget alerts via WebSocket. Built with Spring Boot 4.0 (Java 25) and React 19 (TypeScript), backed by PostgreSQL 18.

**Key Features:**
- Multi-user support with strict data isolation (each user sees only their own data)
- Category and transaction management with full CRUD operations
- Monthly budget tracking with spending aggregations and usage percentage
- Transaction search and filtering (by title, notes, category, date range, amount range)
- Real-time WebSocket budget threshold alerts (50%, 80%, 100%)
- Google and GitHub SSO authentication (OAuth2 / OpenID Connect)
- Docker Compose deployment for the full stack

---

## Table of Contents

1. [How to Run Locally](#how-to-run-locally)
2. [How to Run Tests](#how-to-run-tests)
3. [API Description](#api-description)
4. [Category Deletion Behavior](#category-deletion-behavior)
5. [Google OAuth Configuration](#google-oauth-configuration)
6. [GitHub OAuth Configuration](#github-oauth-configuration)
7. [WebSocket Message Format](#websocket-message-format)

---

## How to Run Locally

### Prerequisites

| Tool       | Version    | Notes                             |
|------------|------------|-----------------------------------|
| Java       | 25         | OpenJDK (e.g., Eclipse Temurin or Zulu) |
| Maven      | 3.8+       | For building/running the backend  |
| Node.js    | 20+        | For building/running the frontend |
| npm        | 10+        | Comes with Node.js                |
| Docker     | 24+        | For PostgreSQL and full-stack deployment |
| Docker Compose | v2+    | Included with Docker Desktop      |

### Option A: Run Services Individually (Development Mode)

This option runs each service separately, which is ideal for development.

**1. Start PostgreSQL:**

```bash
docker compose up -d postgres
```

Wait until the database is healthy:

```bash
docker compose exec postgres pg_isready -U expense_tracker -h localhost -p 5432
```

**2. Start the Backend:**

```bash
cd backend
mvn spring-boot:run
```

The backend API will be available at `http://localhost:8080`.

> **Note:** Without OAuth environment variables configured, the app runs in **fake auth mode** — a hardcoded test user is automatically authenticated. See [Google OAuth Configuration](#google-oauth-configuration) and [GitHub OAuth Configuration](#github-oauth-configuration) to enable real SSO.

**3. Start the Frontend:**

```bash
cd frontend
npm install   # first time only
npm run dev
```

The frontend will be available at `http://localhost:5173`.

### Option B: Run Full Stack with Docker Compose

This option builds and runs all services (PostgreSQL, backend, frontend) in containers.

**1. (Optional) Configure OAuth credentials:**

Copy `.env.example` to `.env` and fill in your OAuth credentials:

```bash
cp .env.example .env
# Edit .env with your Google and/or GitHub OAuth credentials
```

**2. Start all services:**

```bash
docker compose up --build
```

The application will be available at `http://localhost:5173`. The backend API is accessible at `http://localhost:8080`.

**Services:**

| Service    | Port  | Description                |
|------------|-------|----------------------------|
| PostgreSQL | 5432  | Database                   |
| Backend    | 8080  | Spring Boot REST API       |
| Frontend   | 5173  | React app (nginx in Docker)|

---

## How to Run Tests

### Backend Tests

```bash
cd backend
mvn test
```

This runs all JUnit 5 tests including unit tests (Mockito) and integration tests (against PostgreSQL on localhost:5432).

> **Prerequisite:** PostgreSQL must be running (`docker compose up -d postgres`).

### Frontend Tests

```bash
cd frontend
npm test
```

This runs all Vitest tests with React Testing Library.

### Frontend Lint

```bash
cd frontend
npm run lint
```

---

## API Description

All REST endpoints are under the `/api` prefix. Authentication is required for all endpoints except `/api/health`. In OAuth mode, unauthenticated requests receive a `401 Unauthorized` response.

### Health

| Method | Path          | Description     | Auth Required |
|--------|---------------|-----------------|---------------|
| GET    | `/api/health` | Health check    | No            |

**Response (200):**
```json
{
  "status": "UP"
}
```

### Authentication

| Method | Path                              | Description                        | Auth Required |
|--------|-----------------------------------|------------------------------------|---------------|
| GET    | `/api/users/me`                   | Get current authenticated user     | Yes           |
| POST   | `/api/logout`                     | Logout (invalidate session)        | Yes           |
| GET    | `/oauth2/authorization/google`    | Initiate Google OAuth2 login       | No            |
| GET    | `/oauth2/authorization/github`    | Initiate GitHub OAuth2 login       | No            |

**GET `/api/users/me` — Response (200):**
```json
{
  "id": 1,
  "provider": "google",
  "email": "user@example.com",
  "displayName": "John Doe",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

> **Note:** `email` may be `null` for GitHub users with private email settings.

**POST `/api/logout` — Response (200):** Empty body. Session invalidated, `JSESSIONID` cookie deleted.

### Categories

| Method | Path                  | Description                 | Auth Required |
|--------|-----------------------|-----------------------------|---------------|
| GET    | `/api/categories`     | List all user's categories  | Yes           |
| POST   | `/api/categories`     | Create a new category       | Yes           |
| PUT    | `/api/categories/{id}`| Rename a category           | Yes           |
| DELETE | `/api/categories/{id}`| Delete a category           | Yes           |

**POST `/api/categories` — Request:**
```json
{
  "name": "Groceries"
}
```

**POST `/api/categories` — Response (201):**
```json
{
  "id": 1,
  "name": "Groceries",
  "createdAt": "2026-03-23T12:00:00Z"
}
```

**PUT `/api/categories/{id}` — Request:**
```json
{
  "name": "Food & Groceries"
}
```

**PUT `/api/categories/{id}` — Response (200):** Same format as create response.

**DELETE `/api/categories/{id}` — Response (204):** No content.

**Error Responses:**

| Status | Condition                                        |
|--------|--------------------------------------------------|
| 400    | Blank or invalid category name                   |
| 404    | Category not found or belongs to another user    |
| 409    | Duplicate category name for this user            |
| 409    | Category has existing transactions (cannot delete)|

### Transactions

| Method | Path                     | Description                 | Auth Required |
|--------|--------------------------|-----------------------------|---------------|
| GET    | `/api/transactions`      | List/search/filter transactions | Yes       |
| POST   | `/api/transactions`      | Create a new transaction    | Yes           |
| PUT    | `/api/transactions/{id}` | Update a transaction        | Yes           |
| DELETE | `/api/transactions/{id}` | Delete a transaction        | Yes           |

**GET `/api/transactions` — Query Parameters:**

| Parameter    | Type       | Required | Description                                   |
|-------------|------------|----------|-----------------------------------------------|
| `search`    | string     | No       | Search in title and notes (case-insensitive)  |
| `categoryId`| long       | No       | Filter by category ID                          |
| `dateFrom`  | date (ISO) | No       | Filter transactions on or after this date (YYYY-MM-DD) |
| `dateTo`    | date (ISO) | No       | Filter transactions on or before this date (YYYY-MM-DD)|
| `amountMin` | decimal    | No       | Minimum amount (inclusive)                     |
| `amountMax` | decimal    | No       | Maximum amount (inclusive)                     |

**Example:** `GET /api/transactions?search=coffee&categoryId=2&dateFrom=2026-03-01&dateTo=2026-03-31&amountMin=5&amountMax=50`

**POST `/api/transactions` — Request:**
```json
{
  "title": "Morning Coffee",
  "amount": 4.50,
  "transactionDate": "2026-03-23",
  "categoryId": 1,
  "notes": "Starbucks on Main St"
}
```

**POST `/api/transactions` — Response (201):**
```json
{
  "id": 1,
  "title": "Morning Coffee",
  "amount": 4.50,
  "currency": "USD",
  "transactionDate": "2026-03-23",
  "categoryId": 1,
  "categoryName": "Groceries",
  "notes": "Starbucks on Main St",
  "createdAt": "2026-03-23T12:00:00Z"
}
```

**PUT `/api/transactions/{id}` — Request:** Same format as create request.

**PUT `/api/transactions/{id}` — Response (200):** Same format as create response.

**DELETE `/api/transactions/{id}` — Response (204):** No content.

**Error Responses:**

| Status | Condition                                          |
|--------|---------------------------------------------------|
| 400    | Missing or invalid fields (blank title, amount ≤ 0, null date, null categoryId) |
| 404    | Transaction or category not found, or belongs to another user |

### Monthly Budget

| Method | Path            | Description                          | Auth Required |
|--------|-----------------|--------------------------------------|---------------|
| POST   | `/api/budgets`  | Create or update a monthly budget    | Yes           |
| GET    | `/api/budgets`  | Get budget for a specific month      | Yes           |

**POST `/api/budgets` — Request:**
```json
{
  "year": 2026,
  "month": 3,
  "amount": 2000.00
}
```

**POST `/api/budgets` — Response (201 if created, 200 if updated):**
```json
{
  "id": 1,
  "year": 2026,
  "month": 3,
  "amount": 2000.00,
  "createdAt": "2026-03-01T00:00:00Z",
  "updatedAt": "2026-03-23T12:00:00Z"
}
```

**GET `/api/budgets?year=2026&month=3` — Response (200):** Same format as above.

**Error Responses:**

| Status | Condition                                             |
|--------|------------------------------------------------------|
| 400    | Invalid year (not 2000–2100), invalid month (not 1–12), amount ≤ 0 |
| 404    | No budget set for the requested month                 |

### Dashboard

| Method | Path              | Description                              | Auth Required |
|--------|-------------------|------------------------------------------|---------------|
| GET    | `/api/dashboard`  | Get spending summary for a specific month| Yes           |

**GET `/api/dashboard?year=2026&month=3` — Response (200):**
```json
{
  "totalSpent": 1250.75,
  "budgetAmount": 2000.00,
  "remaining": 749.25,
  "usagePercentage": 62.54
}
```

> **Note:** If no budget is set for the requested month, `budgetAmount` is `null` and the UI displays a "No budget set" state.

**Error Responses:**

| Status | Condition                                             |
|--------|------------------------------------------------------|
| 400    | Invalid year (not 2000–2100), invalid month (not 1–12)|

---

## Category Deletion Behavior

This application uses the **"block deletion if transactions exist"** approach for category deletion.

**How it works:**
- When a user attempts to delete a category, the backend checks whether any transactions are associated with that category.
- **If transactions exist:** The deletion is **blocked** and the API returns a `409 Conflict` response with an error message explaining that the category cannot be deleted because it has associated transactions. The user must first delete or reassign those transactions to a different category.
- **If no transactions exist:** The category is deleted successfully and the API returns a `204 No Content` response.

**Rationale:** This approach preserves data integrity by preventing orphaned transactions. It ensures that every transaction always belongs to a valid category, and avoids accidental loss of financial data through cascade deletions.

---

## Google OAuth Configuration

To enable Google SSO authentication, follow these steps:

### 1. Create a Google Cloud Project

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Click **Select a project** → **New Project**
3. Enter a project name (e.g., "Expense Tracker") and click **Create**

### 2. Configure the OAuth Consent Screen

1. Navigate to **APIs & Services** → **OAuth consent screen**
2. Select **External** user type and click **Create**
3. Fill in required fields:
   - **App name:** Expense Tracker
   - **User support email:** Your email address
   - **Developer contact information:** Your email address
4. Click **Save and Continue**
5. On the **Scopes** page, add these scopes:
   - `openid`
   - `email`
   - `profile`
6. Click **Save and Continue** through the remaining steps

### 3. Create OAuth 2.0 Credentials

1. Navigate to **APIs & Services** → **Credentials**
2. Click **Create Credentials** → **OAuth client ID**
3. Select **Web application** as the application type
4. Set the name (e.g., "Expense Tracker Web Client")
5. Add **Authorized JavaScript origins:**
   - `http://localhost:5173` (frontend dev server)
   - `http://localhost:8080` (backend, for Docker mode)
6. Add **Authorized redirect URIs:**
   - `http://localhost:5173/login/oauth2/code/google` (for local development with Vite dev server)
   - `http://localhost:8080/login/oauth2/code/google` (for Docker/production mode)

   > **Note:** The `localhost:5173` URI is needed for local development because the Vite dev server proxies OAuth requests to the backend, causing Spring's `{baseUrl}` to resolve to `http://localhost:5173`.

7. Click **Create**
8. Copy the **Client ID** and **Client Secret**

### 4. Set Environment Variables

Set the following environment variables before starting the backend:

```bash
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
```

Or add them to your `.env` file (see `.env.example`):

```
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
```

When using Docker Compose, these are automatically passed to the backend container.

---

## GitHub OAuth Configuration

To enable GitHub SSO authentication, follow these steps:

### 1. Register a New OAuth App

1. Go to [GitHub Developer Settings](https://github.com/settings/developers)
2. Click **OAuth Apps** → **New OAuth App**
3. Fill in the registration form:
   - **Application name:** Expense Tracker
   - **Homepage URL:** `http://localhost:5173`
   - **Authorization callback URL:** `http://localhost:5173/login/oauth2/code/github` (for local development with Vite dev server)
4. Click **Register application**

> **Note:** GitHub only allows **one** callback URL per OAuth app. The `localhost:5173` URL is correct for local development because the Vite dev server proxies OAuth requests to the backend. For Docker mode, change the callback URL to `http://localhost:8080/login/oauth2/code/github`, or register two separate GitHub OAuth apps (one for dev, one for Docker).

### 2. Generate a Client Secret

1. On the app details page, click **Generate a new client secret**
2. Copy the **Client ID** and **Client Secret** immediately (the secret is only shown once)

### 3. Set Environment Variables

Set the following environment variables before starting the backend:

```bash
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
```

Or add them to your `.env` file (see `.env.example`):

```
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
```

When using Docker Compose, these are automatically passed to the backend container.

> **Note:** GitHub may not provide the user's email depending on their privacy settings. The application identifies users by `provider` + `provider_user_id`, not by email. Google and GitHub sign-ins are treated as separate accounts even if they share the same email address.

---

## WebSocket Message Format

The application uses **STOMP over SockJS** for real-time budget alert notifications.

### Connection Details

| Parameter         | Value                              |
|-------------------|------------------------------------|
| Endpoint          | `/ws` (SockJS)                     |
| Protocol          | STOMP 1.2 over SockJS              |
| Subscribe topic   | `/user/topic/budget-alerts`        |
| Allowed origins   | `http://localhost:5173`            |
| Authentication    | User ID passed via `userId` query parameter on the WebSocket connection URL |

### Connection Flow

1. **Connect:** The client establishes a SockJS connection to `/ws?userId={userId}`.
2. **STOMP CONNECT:** The client sends a STOMP `CONNECT` frame. The server authenticates using the `userId` from the handshake query parameter and associates the connection with the user.
3. **Subscribe (Client → Server):** The client sends a STOMP `SUBSCRIBE` frame to `/user/topic/budget-alerts`. **This subscribe message is required** — without it, no budget alerts are delivered. When the server receives this subscription, it immediately evaluates the current month's budget thresholds and sends any already-crossed alerts.
4. **Receive alerts (Server → Client):** The server pushes `BUDGET_ALERT` messages whenever thresholds are crossed.

### Client-to-Server: Subscribe Message

The client must send a STOMP `SUBSCRIBE` frame to activate alert delivery:

```
SUBSCRIBE
id:sub-0
destination:/user/topic/budget-alerts

^@
```

**Server behavior on subscribe:**
- The server immediately checks the current month's spending against the budget.
- If any thresholds (50%, 80%, 100%) are already crossed, those alerts are sent right away.
- This ensures the client receives alerts for thresholds that were crossed before the WebSocket connection was established.

### Server-to-Client: Budget Alert Message

When a budget threshold is crossed, the server sends a JSON message:

```json
{
  "type": "BUDGET_ALERT",
  "threshold": 80,
  "currentSpending": 1600.00,
  "budgetAmount": 2000.00,
  "yearMonth": "2026-03"
}
```

**Fields:**

| Field             | Type    | Description                                      |
|-------------------|---------|--------------------------------------------------|
| `type`            | string  | Always `"BUDGET_ALERT"`                          |
| `threshold`       | integer | The threshold percentage crossed: `50`, `80`, or `100` |
| `currentSpending` | decimal | The user's total spending for the month          |
| `budgetAmount`    | decimal | The user's budget for the month                  |
| `yearMonth`       | string  | The year-month in `YYYY-MM` format               |

### Budget Alert Rules

1. **Thresholds:** Alerts are generated for three thresholds — **50%**, **80%**, and **100%** of the monthly budget.

2. **Current month only:** Alerts are calculated for the **current calendar month** only.

3. **Once per threshold per month:** Each threshold fires **at most once** per user per month. Once a 50% alert has been sent, it will not be sent again for that month even if spending fluctuates around the 50% mark (e.g., after deleting transactions).

4. **Alert triggers:**
   - **On subscribe:** When the client subscribes to `/user/topic/budget-alerts`, any already-crossed thresholds are sent immediately.
   - **On transaction create/update/delete:** After any transaction mutation, the server recalculates spending and fires alerts for any newly crossed thresholds.

5. **No budget = no alerts:** If no monthly budget is set for the current month, no alerts are generated regardless of spending.

6. **Unauthenticated connections rejected:** WebSocket handshakes without a valid `userId` query parameter are rejected.

### Example Scenario

1. User sets a budget of $1,000 for March 2026.
2. User creates a transaction of $550 → **50% alert fires** (`currentSpending: 550.00`).
3. User creates another transaction of $300 → **80% alert fires** (`currentSpending: 850.00`).
4. User deletes the $300 transaction (spending drops to $550) → **no new alert** (50% already fired, 80% already fired — once per month).
5. User creates a transaction of $500 → **100% alert fires** (`currentSpending: 1050.00`).
