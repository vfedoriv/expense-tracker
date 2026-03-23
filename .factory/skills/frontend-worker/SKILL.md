---
name: frontend-worker
description: Implements frontend features — React components, pages, hooks, API integration, Tailwind CSS styling, and tests
---

# Frontend Worker

NOTE: Startup and cleanup are handled by `worker-base`. This skill defines the WORK PROCEDURE.

## When to Use This Skill

Frontend-only features: React pages, components, hooks, API client integration, routing, styling with Tailwind CSS, form validation, responsive layout, frontend tests.

## Required Skills

- **agent-browser**: For manual verification of UI. Invoke after implementation to verify pages render correctly, forms work, responsive behavior, empty states, loading states, hover/focus states.

## Work Procedure

1. **Read context**: Read `mission.md` for frontend architecture and UI requirements. Read `.factory/library/architecture.md` for patterns. Read `.factory/services.yaml` for commands.

2. **Understand the feature**: Read the feature description, preconditions, expectedBehavior, and verificationSteps. Identify all UI requirements.

3. **Write tests FIRST (TDD red phase)**:
   - Write failing component tests (Vitest + React Testing Library)
   - Test rendering, user interactions, form validation, state changes
   - Run `cd frontend && npm test` — confirm tests fail

4. **Implement to make tests pass (TDD green phase)**:
   - Create/update React components with TypeScript
   - Use Tailwind CSS for all styling (no inline styles or CSS modules)
   - Implement hooks for data fetching and state management
   - Add client-side validation with clear error messages
   - Add loading states, empty states, error states
   - Ensure hover/focus states on interactive elements
   - Ensure responsive behavior (test at 375px and 1280px)

5. **Run all tests and lint**:
   - `cd frontend && npm test` — ALL tests pass
   - `cd frontend && npm run lint` — no lint errors
   - `cd frontend && npx tsc --noEmit` — no type errors

6. **Manual verification with agent-browser**:
   - Ensure backend and frontend are running
   - Use agent-browser to navigate to the page
   - Verify: content renders, forms submit, validation works, empty states show, loading states appear
   - Verify responsive behavior at narrow viewport
   - Take at least 2 screenshots (desktop + mobile width)
   - Each check = one `interactiveChecks` entry

7. **Update shared state**: Update `.factory/library/architecture.md` with new patterns/components discovered.

### Key Frontend Conventions
- Tailwind CSS for all styling — use utility classes
- TypeScript strict mode — no `any` types
- Custom hooks for API calls (e.g., `useCategories()`, `useTransactions()`)
- API client in `src/api/client.ts` with base URL and auth headers
- Error handling: try/catch in hooks, display errors in UI
- Empty states: dedicated component with message and optional icon
- Loading states: spinner or skeleton component
- Forms: controlled components with validation state

## Example Handoff

```json
{
  "salientSummary": "Built the Transactions page with list/table view, create/edit modal, delete with confirmation. All client-side validation implemented (title required, amount > 0, date required, category required). Verified with agent-browser: forms submit correctly, validation errors show inline, empty state displays when no transactions, responsive cards layout at 375px.",
  "whatWasImplemented": "TransactionsPage component with transaction list (table at desktop, cards at mobile). CreateEditTransactionModal with form validation. DeleteConfirmationDialog. useTransactions hook for CRUD operations. Transaction TypeScript types. Search input integration. Filter controls (category, date range, amount range). Empty state, loading spinner, error toast components.",
  "whatWasLeftUndone": "",
  "verification": {
    "commandsRun": [
      {"command": "cd frontend && npm test", "exitCode": 0, "observation": "18 tests passed, 0 failed"},
      {"command": "cd frontend && npm run lint", "exitCode": 0, "observation": "no lint errors"},
      {"command": "cd frontend && npx tsc --noEmit", "exitCode": 0, "observation": "no type errors"}
    ],
    "interactiveChecks": [
      {"action": "Navigated to /transactions, created a transaction with all fields", "observed": "Transaction appeared in list with correct values, modal closed on success"},
      {"action": "Submitted form with empty title", "observed": "Inline validation error 'Title is required' displayed, form did not submit"},
      {"action": "Resized to 375px viewport", "observed": "Table converted to card layout, all fields readable, create button accessible"},
      {"action": "Viewed page with no transactions", "observed": "Empty state message 'No transactions yet' with create button displayed"}
    ]
  },
  "tests": {
    "added": [
      {"file": "src/__tests__/TransactionsPage.test.tsx", "cases": [
        {"name": "renders transaction list", "verifies": "displays transactions from API"},
        {"name": "shows empty state when no transactions", "verifies": "empty state message rendered"},
        {"name": "validates required fields on create", "verifies": "client-side validation errors"},
        {"name": "submits valid form", "verifies": "API call made with correct data"}
      ]}
    ]
  },
  "discoveredIssues": []
}
```

## When to Return to Orchestrator

- Backend API endpoint doesn't exist yet (needs backend feature first)
- API response format doesn't match expected types
- Cannot install a required npm dependency
- Tailwind CSS configuration issues that affect the whole project
