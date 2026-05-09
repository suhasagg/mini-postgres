#!/usr/bin/env bash
set -euo pipefail
PORT="${PORT:-8080}"
BASE="http://localhost:${PORT}"

curl -s -X POST "$BASE/sql" -H 'Content-Type: text/plain' --data "CREATE TABLE users (id INT, name TEXT, age INT, city TEXT);" | jq
curl -s -X POST "$BASE/sql" -H 'Content-Type: text/plain' --data "INSERT INTO users (id, name, age, city) VALUES (1, 'Suhas', 35, 'Delhi');" | jq
curl -s -X POST "$BASE/sql" -H 'Content-Type: text/plain' --data "INSERT INTO users (id, name, age, city) VALUES (2, 'Alice', 31, 'Bangalore');" | jq
curl -s -X POST "$BASE/sql" -H 'Content-Type: text/plain' --data "CREATE INDEX idx_users_id ON users(id);" | jq
curl -s -X POST "$BASE/sql" -H 'Content-Type: text/plain' --data "EXPLAIN SELECT * FROM users WHERE id = 1;" | jq
curl -s -X POST "$BASE/sql" -H 'Content-Type: text/plain' --data "SELECT id, name, city FROM users WHERE age >= 30 ORDER BY name LIMIT 10;" | jq
