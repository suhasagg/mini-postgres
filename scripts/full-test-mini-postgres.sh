#!/usr/bin/env bash
set -euo pipefail

# Full end-to-end test suite for mini-postgres-java.
# It verifies:
#   1. Java/JDK availability
#   2. Compile/build
#   3. Built-in Java unit tests
#   4. Direct engine transaction semantics: BEGIN / COMMIT / ROLLBACK
#   5. HTTP server health
#   6. SQL DDL and DML through /sql
#   7. SELECT / WHERE / ORDER BY / LIMIT
#   8. UPDATE and DELETE
#   9. CREATE INDEX and EXPLAIN index-plan selection
#  10. Typed values: INT, LONG, DOUBLE, BOOLEAN, TEXT
#  11. Persistence after server restart
#
# Usage:
#   chmod +x scripts/full-test.sh
#   ./scripts/full-test.sh
#
# Optional:
#   PORT=18080 ./scripts/full-test.sh

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

PORT="${PORT:-$((20000 + RANDOM % 20000))}"
BASE="http://localhost:${PORT}"
TMP_ROOT="$(mktemp -d)"
SERVER_DATA_DIR="$TMP_ROOT/server-data"
ENGINE_DATA_DIR="$TMP_ROOT/engine-data"
SERVER_LOG="$TMP_ROOT/server.log"
SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

pass() { printf '\033[0;32m[PASS]\033[0m %s\n' "$1"; }
fail() { printf '\033[0;31m[FAIL]\033[0m %s\n' "$1" >&2; exit 1; }
info() { printf '\033[0;34m[INFO]\033[0m %s\n' "$1"; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_cmd java
require_cmd javac
require_cmd curl
require_cmd jq

assert_jq() {
  local json="$1"
  local expr="$2"
  local message="$3"
  echo "$json" | jq -e "$expr" >/dev/null || {
    echo "--- JSON response ---" >&2
    echo "$json" | jq . >&2 || echo "$json" >&2
    fail "$message"
  }
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local message="$3"
  [[ "$haystack" == *"$needle"* ]] || {
    echo "--- Actual text ---" >&2
    echo "$haystack" >&2
    fail "$message"
  }
}

post_sql() {
  local query="$1"
  curl -sS -X POST "$BASE/sql" \
    -H 'Content-Type: text/plain' \
    --data "$query"
}

post_sql_ok() {
  local query="$1"
  local json
  json="$(post_sql "$query")"
  assert_jq "$json" '.success == true' "SQL failed: $query"
  echo "$json"
}

wait_for_server() {
  local attempts=60
  for _ in $(seq 1 "$attempts"); do
    if [[ -n "${SERVER_PID:-}" ]] && ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
      echo "--- Server log ---" >&2
      cat "$SERVER_LOG" >&2 || true
      fail "Server process exited before becoming healthy. Port may already be in use: $PORT"
    fi
    if curl -sS -f "$BASE/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.25
  done
  echo "--- Server log ---" >&2
  cat "$SERVER_LOG" >&2 || true
  fail "Server did not become healthy at $BASE"
}

start_server() {
  mkdir -p "$SERVER_DATA_DIR"
  DATA_DIR="$SERVER_DATA_DIR" PORT="$PORT" ./scripts/run-server.sh >"$SERVER_LOG" 2>&1 &
  SERVER_PID="$!"
  wait_for_server
}

stop_server() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    kill "$SERVER_PID" >/dev/null 2>&1 || true
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  SERVER_PID=""
}

info "Checking Java versions"
java -version 2>&1 | head -n 1
javac -version
pass "Java is available"

info "Building project"
./scripts/build.sh >/dev/null
[[ -f build/mini-postgres.jar ]] || fail "build/mini-postgres.jar was not generated"
pass "Project builds successfully"

info "Running built-in unit tests"
./scripts/test.sh
pass "Built-in unit tests passed"

info "Running direct engine transaction and persistence checks"
mkdir -p "$TMP_ROOT/engine-check-classes"
cat > "$TMP_ROOT/FullEngineCheck.java" <<'JAVA'
import com.example.minipostgres.engine.MiniPostgresDatabase;
import com.example.minipostgres.engine.QueryResult;
import com.example.minipostgres.engine.Session;

import java.nio.file.Path;

public final class FullEngineCheck {
    private static void ok(boolean condition, String message) {
        if (!condition) throw new RuntimeException(message);
    }

    private static QueryResult exec(MiniPostgresDatabase db, Session session, String sql) {
        QueryResult result = db.execute(session, sql);
        ok(result.success(), "SQL failed: " + sql + " -> " + result.message());
        return result;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);

        try (MiniPostgresDatabase db = new MiniPostgresDatabase(dir)) {
            Session s = new Session();
            exec(db, s, "CREATE TABLE tx_users (id INT, name TEXT, score INT);");

            exec(db, s, "BEGIN;");
            exec(db, s, "INSERT INTO tx_users (id, name, score) VALUES (1, 'rollback-user', 10);");
            exec(db, s, "ROLLBACK;");
            QueryResult afterRollback = exec(db, s, "SELECT * FROM tx_users WHERE id = 1;");
            ok(afterRollback.rows().isEmpty(), "ROLLBACK should remove inserted row");

            exec(db, s, "BEGIN;");
            exec(db, s, "INSERT INTO tx_users (id, name, score) VALUES (2, 'commit-user', 20);");
            exec(db, s, "COMMIT;");
            QueryResult afterCommit = exec(db, s, "SELECT name, score FROM tx_users WHERE id = 2;");
            ok(afterCommit.rows().size() == 1, "COMMIT should persist inserted row");
            ok("commit-user".equals(afterCommit.rows().get(0).get("name")), "Committed row name mismatch");

            exec(db, s, "CREATE INDEX idx_tx_users_id ON tx_users(id);");
            QueryResult plan = exec(db, s, "EXPLAIN SELECT * FROM tx_users WHERE id = 2;");
            ok(plan.rows().get(0).get("plan").toString().contains("IndexScan"), "Expected IndexScan plan");
        }

        try (MiniPostgresDatabase reopened = new MiniPostgresDatabase(dir)) {
            Session s = new Session();
            QueryResult persisted = exec(reopened, s, "SELECT name, score FROM tx_users WHERE id = 2;");
            ok(persisted.rows().size() == 1, "Committed row should survive database reopen");
            ok("commit-user".equals(persisted.rows().get(0).get("name")), "Persisted row name mismatch");
        }
    }
}
JAVA
javac --release 17 -cp build/classes -d "$TMP_ROOT/engine-check-classes" "$TMP_ROOT/FullEngineCheck.java"
java -cp "build/classes:$TMP_ROOT/engine-check-classes" FullEngineCheck "$ENGINE_DATA_DIR"
pass "Direct engine transaction and persistence checks passed"

info "Starting HTTP server on $BASE"
start_server
pass "HTTP server is healthy"

health_json="$(curl -sS -f "$BASE/health")"
assert_jq "$health_json" '.status == "UP" and .service == "mini-postgres"' "Health response is invalid"
pass "Health endpoint returned UP"

info "Running HTTP SQL end-to-end checks"

json="$(post_sql_ok "CREATE TABLE users (id INT, name TEXT, age INT, city TEXT, active BOOLEAN);")"
assert_jq "$json" '.message == "CREATE TABLE users"' "CREATE TABLE response mismatch"
pass "CREATE TABLE users"

post_sql_ok "INSERT INTO users (id, name, age, city, active) VALUES (1, 'Suhas', 35, 'Delhi', true);" >/dev/null
post_sql_ok "INSERT INTO users (id, name, age, city, active) VALUES (2, 'Alice', 31, 'Bangalore', true);" >/dev/null
post_sql_ok "INSERT INTO users (id, name, age, city, active) VALUES (3, 'Bob', 28, 'Mumbai', false);" >/dev/null
pass "INSERT rows"

json="$(post_sql_ok "SHOW TABLES;")"
assert_jq "$json" '.rows | length == 1' "SHOW TABLES should return one table"
assert_jq "$json" '.rows[0].table == "users"' "SHOW TABLES should include users"
pass "SHOW TABLES"

json="$(post_sql_ok "DESCRIBE users;")"
assert_jq "$json" '.rows | length == 5' "DESCRIBE users should return five columns"
assert_jq "$json" '.rows[] | select(.column == "id" and .type == "INT")' "DESCRIBE should include id INT"
assert_jq "$json" '.rows[] | select(.column == "active" and .type == "BOOLEAN")' "DESCRIBE should include active BOOLEAN"
pass "DESCRIBE users"

json="$(post_sql_ok "SELECT id, name, city FROM users WHERE age >= 30 ORDER BY name LIMIT 10;")"
assert_jq "$json" '.rows | length == 2' "SELECT age >= 30 should return two rows"
assert_jq "$json" '.rows[0].name == "Alice" and .rows[1].name == "Suhas"' "ORDER BY name should sort Alice before Suhas"
pass "SELECT with WHERE, ORDER BY, LIMIT"

json="$(post_sql_ok "UPDATE users SET city = 'Gurgaon' WHERE id = 2;")"
assert_jq "$json" '.updatedRows == 1' "UPDATE should affect one row"
json="$(post_sql_ok "SELECT city FROM users WHERE id = 2;")"
assert_jq "$json" '.rows[0].city == "Gurgaon"' "Updated city should be Gurgaon"
pass "UPDATE and read back"

json="$(post_sql_ok "DELETE FROM users WHERE id = 3;")"
assert_jq "$json" '.updatedRows == 1' "DELETE should affect one row"
json="$(post_sql_ok "SELECT id, name FROM users WHERE id = 3;")"
assert_jq "$json" '.rows | length == 0' "Deleted row should not be returned"
pass "DELETE and tombstone-style visibility"

post_sql_ok "CREATE INDEX idx_users_id ON users(id);" >/dev/null
json="$(post_sql_ok "EXPLAIN SELECT * FROM users WHERE id = 1;")"
assert_jq "$json" '.rows[0].plan | contains("IndexScan")' "EXPLAIN should use IndexScan after CREATE INDEX"
assert_jq "$json" '.rows[0].plan | contains("idx_users_id")' "EXPLAIN should reference idx_users_id"
pass "CREATE INDEX and EXPLAIN IndexScan"

json="$(post_sql_ok "SELECT name, active FROM users WHERE id = 1;")"
assert_jq "$json" '.rows[0].name == "Suhas" and .rows[0].active == true' "Boolean/text row mismatch"
pass "Typed TEXT and BOOLEAN values"

post_sql_ok "CREATE TABLE metrics (id INT, event_time LONG, score DOUBLE, ok BOOLEAN, note TEXT);" >/dev/null
post_sql_ok "INSERT INTO metrics (id, event_time, score, ok, note) VALUES (100, 1710000000000, 98.75, true, 'latency-ok');" >/dev/null
json="$(post_sql_ok "SELECT id, event_time, score, ok, note FROM metrics WHERE score > 90 ORDER BY id LIMIT 5;")"
assert_jq "$json" '.rows | length == 1' "metrics SELECT should return one row"
assert_jq "$json" '.rows[0].event_time == 1710000000000 and .rows[0].score == 98.75 and .rows[0].ok == true' "LONG/DOUBLE/BOOLEAN values mismatch"
pass "Typed INT, LONG, DOUBLE, BOOLEAN, TEXT values"

info "Testing HTTP persistence after server restart"
stop_server
start_server
json="$(post_sql_ok "SELECT name, city FROM users WHERE id = 2;")"
assert_jq "$json" '.rows | length == 1' "Persisted users row id=2 should exist after restart"
assert_jq "$json" '.rows[0].name == "Alice" and .rows[0].city == "Gurgaon"' "Persisted users row id=2 mismatch after restart"
json="$(post_sql_ok "EXPLAIN SELECT * FROM users WHERE id = 1;")"
assert_jq "$json" '.rows[0].plan | contains("IndexScan")' "Index should be restored after restart"
pass "Persistence after server restart"

info "Checking generated storage files"
[[ -f "$SERVER_DATA_DIR/catalog.meta" ]] || fail "catalog.meta not found"
[[ -f "$SERVER_DATA_DIR/tables/users.heap" ]] || fail "users.heap not found"
[[ -f "$SERVER_DATA_DIR/wal/mini-pg.wal" ]] || fail "wal/mini-pg.wal not found"
pass "Catalog, heap, and WAL files exist"

stop_server

echo
pass "FULL MINI-POSTGRES TEST SUITE PASSED"
