#!/usr/bin/env bash
# Applies the migrations to a scratch database twice (to prove they are re-runnable) and runs
# the RLS checks. Needs a local Postgres; set PGURL (and PGPASSWORD if needed) to override.
set -euo pipefail
cd "$(dirname "$0")"
PGURL=${PGURL:-postgres://postgres@127.0.0.1:5432/postgres}
DB=stepq_app_test
psql "$PGURL" -qc "drop database if exists $DB" -c "create database $DB"
URL="${PGURL%/*}/$DB"
psql "$URL" -q -v ON_ERROR_STOP=1 -f supabase_stub.sql
for f in ../migrations/*.sql; do psql "$URL" -q -v ON_ERROR_STOP=1 -f "$f"; done
for f in ../migrations/*.sql; do psql "$URL" -q -v ON_ERROR_STOP=1 -f "$f"; done
psql "$URL" -qtA -v ON_ERROR_STOP=1 -f rls_test.sql 2>&1 | grep -v "^$"
