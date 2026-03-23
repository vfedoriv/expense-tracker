#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Start PostgreSQL if not running
if ! docker compose -f "$REPO_ROOT/docker-compose.yml" ps postgres 2>/dev/null | grep -q "running"; then
  echo "Starting PostgreSQL..."
  docker compose -f "$REPO_ROOT/docker-compose.yml" up -d postgres
  echo "Waiting for PostgreSQL to be ready..."
  for i in $(seq 1 30); do
    if docker compose -f "$REPO_ROOT/docker-compose.yml" exec -T postgres pg_isready -U expense_tracker -h localhost -p 5432 >/dev/null 2>&1; then
      echo "PostgreSQL is ready."
      break
    fi
    sleep 1
  done
fi

# Install frontend dependencies if node_modules is missing or outdated
if [ ! -d "$REPO_ROOT/frontend/node_modules" ]; then
  echo "Installing frontend dependencies..."
  cd "$REPO_ROOT/frontend" && npm install
fi

# Resolve backend Maven dependencies (idempotent, fast if cached)
if [ -f "$REPO_ROOT/backend/pom.xml" ]; then
  echo "Resolving backend Maven dependencies..."
  cd "$REPO_ROOT/backend" && mvn dependency:resolve -q 2>/dev/null || true
fi

echo "Init complete."
