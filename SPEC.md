## Personal Expense Tracker

Create a full-stack web app that demonstrates end-to-end product implementation (frontend + backend + persistence + tests). 

### Business scenario:
 A user tracks personal spending across categories to control a monthly budget, quickly find transactions, and identify overspending trends.

## 0) Stack
- Backend language: Java 25
- Backend framework: Spring Boot 4.0, Maven 3.9
- Database: PostgreSQL 18
- Frontend language: Typescript
- Frontend library: React 18+ , Vite, Tailwind CSS
- Tests: unit tests - Junit, Mockito; integration tests – Testcontainers, WireMock
- Hosting/Deployment: Docker compose
- Monorepo that contains folders ‘/frontend’ for frontend part, ‘/backend’ for backend part; other required folders (configs, scripts for database init etc.)


## 1) Core rules
- The application supports multiple users
- Each user sees only their own data
- There is no sharing of any kind (no invites, no public links, no shared budgets)

## 2) Functional requirements

#### 2.1.Accounts and authentication (SSO only)
- Authentication must be implemented via SSO only (fake as a first step)
- The app must support both providers:
    - Google OAuth / OpenID Connect
    - GitHub OAuth
- The app must support logout
- Authentication must persist across page refresh
- On first successful SSO sign-in, the backend must create a local user record automatically
- The backend must persist at least the following user profile data:
    - Provider
    - provider_user_id
    - email if provided by the provider
    - display_name
    - avatar_url as optional
- Account linking between Google and GitHub is not required
- If the same person signs in with Google and GitHub, these may be treated as separate accounts

#### 2.2) Categories
A category represents how the user groups expenses.
- A user can create a category
- A user can rename a category
- A user can delete a category
- Category name must be unique per user
- Deleting a category must be handled by this approach:
    - Block deletion if transactions exist in the category
- The deleting approach must be documented in README

#### 2.3) Transactions (expenses)
Each transaction belongs to exactly one user and must include:
    - Title (short description)
    - Amount
    - Currency (single currency is acceptable for MVP, but it must be explicit in the UI)
    - Transaction date
    - Category
    - Notes

Transaction operations:
    - Create a transaction
    - Edit any transaction field
    - Delete a transaction

Validation rules:
    - Amount must be greater than 0
    - Transaction date must be a valid date
    - Title must be non-empty

#### 2.4) Monthly budget
- A user can set a monthly budget amount for a selected month
- The app calculates:
    - Total spent for the selected month
    - Remaining budget (budget minus spent)
    - Budget usage percentage
    - If no budget is set for a month, the UI must show a clear “No budget set” state instead of misleading numbers

#### 2.5) Search and filters
- Search transactions by title and notes
- Filter transactions by category
- Filter transactions by date range: this month, last month, custom range
- Filter transactions by amount range: min/max

#### 2.6) Real-time communication (WebSocket budget alerts)
The app must include two-way communication using WebSocket.

##### Server → client behavior:
   - The backend must push real-time budget threshold alerts over WebSocket
   - Alerts are calculated for the current calendar month only
   - Required thresholds:
        - 50%
        - 80%
        - 100%
   - A threshold alert must fire once per threshold per month
   - Alerts must be generated:
        - When the WebSocket connection opens
        - After a transaction is created, updated, or deleted
   - If no monthly budget is set for the current calendar month, threshold alerts must not be generated

##### Client → server behavior:
   - The client must send at least one meaningful WebSocket message to the server
   - Acceptable examples:
        - Subscribe
        - Ack
   - The chosen message type and payload format must be documented in README
   - The message must affect server behavior in some meaningful way, for example:
        - subscribing to budget alerts
        - acknowledging an alert as read

##### UI behavior for alerts:
   - Budget alerts must be visible in the UI
   - Acceptable presentation:
        - toast notifications
        - alert banner
        - notification panel

## 3) UI requirements (modern, styled, responsive, interactive)
- Authentication entry screen with:
    - Continue with Google
    - Continue with GitHub
- Main dashboard screen that shows for a selected month:
    - Total spent
    - Budget amount
    - Remaining budget
    - Budget usage percentage
- Transactions screen or section with:
    - Transactions list or table
    - Search input
    - Filter controls (category, date range, amount range)
    - Create/Edit transaction UI (modal, drawer, or separate page is acceptable)
- Categories management UI (page or modal)
- Visible real-time budget alerts while connected to WebSocket
- Responsive behavior for narrow screens:
    - Table-to-cards is acceptable
    - Horizontal scroll is acceptable

To make “modern, nice, styled, responsive, interactive” checkable, the UI must also include:
- Consistent spacing and typography across major screens
- Visible hover and focus states for interactive elements
- Clear empty states for:
    - No transactions
    - No categories
    - No search results
    - No budget set
- At least one visible loading state on a main screen or major data block
- Client-side validation feedback for transaction create/edit forms
- Light theme only. Dark mode is not required

## 4) Backend requirements
- Provide an HTTP API that supports all UI flows
- Enforce authorization for every category/transaction/budget operation
- Enforce authorization on WebSocket connections and budget alert delivery
- Validate inputs and return clear error responses for invalid requests
- Implement Google and GitHub SSO securely
- Persist data in a database chosen by the developer
- Document required environment variables for Google OAuth and GitHub OAuth in README

## 5) Quality requirements
- Include automated tests covering at least:
    - SSO login success path in test mode using a mock or stub provider response
    - Create category and create transaction
    - Authorization: user cannot access another user’s categories/transactions/budgets
    - Budget threshold WebSocket alerts for 50%, 80%, and 100%
- Basic error handling must be visible in the UI
- Tests must not depend on real Google or GitHub network calls
- The project must start locally with documented commands

## 6) Authentication implementation requirements
- As a first step we should provide fake auth to simplify developments
- Only after we implement all business logic, we will implement full SSO auth
- The idea is that backend expects something like a “User Context” (principal + roles); during early development, we fill this with a FakeUser (e.g., always “admin@test.com”); later, we replace FakeUser with SSO provider (Google/Github)


## 7) Deliverables
- Repository with frontend and backend source code
- README that includes:
    - How to run backend and frontend locally
    - How to run tests
    - Short description of API
    - Explanation of category deletion behavior (block vs reassign)
    - How to configure Google OAuth credentials
    - How to configure GitHub OAuth credentials
    - WebSocket message format and budget alert rules
- Containerization via docker-compose


## 8) Acceptance checklist
- A user can sign in with Google and GitHub
- A local user record is created automatically on first successful SSO sign-in
- A user can create categories and transactions
- A user can set a monthly budget and see totals, remaining budget, and usage percentage
- A user can search and filter transactions
- Data is private per user (no cross-account access)
- While connected, the app receives real-time budget alerts for 50%, 80%, and 100% thresholds for the current calendar month
- The WebSocket flow includes at least one client → server message that changes server behavior
- App runs locally from README instructions
- Tests pass locally

## 9) Additional requirements 
- GitHub may not always provide email depending on user settings, so identity should rely on provider + provider_user_id
- If the user edits or deletes transactions after crossing a threshold, alerts should still follow the “once per threshold per month” rule and not spam repeatedly
