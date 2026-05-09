# Mini Postgres Java

A dependency-free educational implementation of a PostgreSQL-like relational database in Java.

This is **not a production database** and it is not wire-compatible with PostgreSQL. It is a compact learning project that implements the core architecture ideas behind a relational database engine: SQL parsing, catalog metadata, heap-style table storage, typed rows, write-ahead logging, transactions, indexes, planning, execution, and a small HTTP SQL endpoint.

## Features

- SQL subset inspired by PostgreSQL
- `CREATE TABLE`
- `INSERT`
- `SELECT`
- `UPDATE`
- `DELETE`
- `CREATE INDEX`
- `BEGIN`, `COMMIT`, `ROLLBACK`
- `EXPLAIN SELECT ...`
- `SHOW TABLES`
- `DESCRIBE table`
- Typed columns: `INT`, `LONG`, `DOUBLE`, `BOOLEAN`, `TEXT`
- Catalog metadata persisted to disk
- Heap-style table files
- Write-ahead log / WAL
- Transaction manager with rollback undo log
- Simple MVCC-inspired row metadata: created/deleted transaction ids
- B-tree-like secondary index using Java `TreeMap`
- Index-backed equality and range selection
- Query planner choosing sequential scan vs index scan
- Projection, filtering, ordering, and limit
- CLI SQL shell
- HTTP SQL API using Java built-in `HttpServer`
- No Maven, Gradle, or external dependency required
- Plain `javac` build scripts
- Custom Java test runner

---

## 1. Architecture

```text
                  ┌──────────────────────────┐
                  │      Client / CLI         │
                  │  SQL shell or HTTP /sql   │
                  └─────────────┬────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │      SQL Frontend         │
                  │ - parser                  │
                  │ - statement objects       │
                  └─────────────┬────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │       Query Engine        │
                  │ - statement dispatch      │
                  │ - transaction boundaries  │
                  │ - result formatting       │
                  └─────────────┬────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
 ┌───────────────────┐ ┌──────────────────┐ ┌───────────────────┐
 │      Planner       │ │      Catalog     │ │ TransactionManager │
 │ - seq scan         │ │ - schemas        │ │ - tx ids           │
 │ - index scan       │ │ - indexes        │ │ - undo log         │
 └─────────┬─────────┘ └─────────┬────────┘ └─────────┬─────────┘
           │                     │                    │
           ▼                     ▼                    ▼
 ┌──────────────────────────────────────────────────────────────┐
 │                         Storage Layer                         │
 │  Heap table files + WAL + in-memory B-tree-like indexes       │
 │                                                              │
 │  tables/users.heap     catalog.meta      wal/mini-pg.wal      │
 └──────────────────────────────────────────────────────────────┘
```

---

## 2. PostgreSQL-like concepts implemented

| PostgreSQL concept | Implemented here |
|---|---|
| SQL frontend | `SqlParser` |
| Parser output | `Statement` classes |
| Catalog tables | `Catalog`, `TableMetadata`, `Column` |
| Heap storage | `Table` persisted as `.heap` file |
| Row id / tuple id | `rowId` |
| WAL | `WriteAheadLog` |
| Transactions | `TransactionManager`, `Transaction` |
| Rollback | transaction undo actions |
| MVCC-inspired metadata | `createdTx`, `deletedTx`, `deleted` |
| B-tree index | `BTreeIndex` backed by `TreeMap<ValueKey, Set<RowId>>` |
| Query planner | `QueryPlanner` |
| Sequential scan | `SeqScanPlan` |
| Index scan | `IndexScanPlan` |
| Executor | `Table.select`, `QueryEngine` |
| Explain plan | `EXPLAIN SELECT ...` |
| HTTP SQL endpoint | `MiniPostgresHttpServer` |

---

## 3. Requirements

- Java 17+
- Bash shell

No Maven or Gradle is required.

Check Java:

```bash
java -version
javac -version
```

---

## 4. Build

```bash
./scripts/build.sh
```

This creates:

```text
build/mini-postgres.jar
```

---

## 5. Run tests

```bash
./scripts/test.sh
```

Expected output:

```text
[PASS] SqlParserTest
[PASS] QueryEngineTest
[PASS] TransactionTest
[PASS] IndexPlannerTest
[PASS] PersistenceTest
All tests passed.
```

---

## 6. Run CLI shell

```bash
./scripts/run-cli.sh
```

Example:

```sql
CREATE TABLE users (id INT, name TEXT, age INT, city TEXT);
INSERT INTO users (id, name, age, city) VALUES (1, 'Suhas', 35, 'Delhi');
INSERT INTO users (id, name, age, city) VALUES (2, 'Alice', 31, 'Bangalore');
CREATE INDEX idx_users_id ON users(id);
EXPLAIN SELECT * FROM users WHERE id = 1;
SELECT id, name, city FROM users WHERE age >= 30 ORDER BY name LIMIT 10;
```

Exit with:

```sql
.exit
```

---

## 7. Run HTTP server

```bash
./scripts/run-server.sh
```

Default server:

```text
http://localhost:8080
```

Override settings:

```bash
PORT=9090 DATA_DIR=/tmp/mini-postgres-data ./scripts/run-server.sh
```

---

## 8. API examples

### 8.1 Health

```bash
curl -s http://localhost:8080/health | jq
```

Example response:

```json
{
  "status": "UP",
  "service": "mini-postgres",
  "tables": 0
}
```

### 8.2 Execute SQL

```bash
curl -s -X POST http://localhost:8080/sql \
  -H 'Content-Type: text/plain' \
  --data "CREATE TABLE users (id INT, name TEXT, age INT);" | jq
```

```bash
curl -s -X POST http://localhost:8080/sql \
  -H 'Content-Type: text/plain' \
  --data "INSERT INTO users (id, name, age) VALUES (1, 'Suhas', 35);" | jq
```

```bash
curl -s -X POST http://localhost:8080/sql \
  -H 'Content-Type: text/plain' \
  --data "SELECT * FROM users WHERE id = 1;" | jq
```

---

## 9. Supported SQL subset

### Create table

```sql
CREATE TABLE users (id INT, name TEXT, age INT, active BOOLEAN);
```

### Insert

```sql
INSERT INTO users (id, name, age, active)
VALUES (1, 'Suhas', 35, true);
```

### Select

```sql
SELECT * FROM users;
SELECT id, name FROM users WHERE age >= 30;
SELECT id, name FROM users WHERE age >= 30 AND active = true ORDER BY name LIMIT 10;
```

### Create index

```sql
CREATE INDEX idx_users_id ON users(id);
```

### Explain

```sql
EXPLAIN SELECT * FROM users WHERE id = 1;
```

### Update

```sql
UPDATE users SET city = 'Delhi NCR' WHERE id = 1;
```

### Delete

```sql
DELETE FROM users WHERE id = 1;
```

### Transactions

```sql
BEGIN;
INSERT INTO users (id, name, age) VALUES (2, 'Alice', 31);
ROLLBACK;

BEGIN;
INSERT INTO users (id, name, age) VALUES (3, 'Bob', 29);
COMMIT;
```

---

## 10. Source code map

```text
src/main/java/com/example/minipostgres
├── Main.java
├── catalog
│   ├── Catalog.java
│   ├── Column.java
│   ├── DataType.java
│   └── TableMetadata.java
├── engine
│   ├── MiniPostgresDatabase.java
│   ├── QueryEngine.java
│   ├── QueryResult.java
│   └── Session.java
├── index
│   ├── BTreeIndex.java
│   └── ValueKey.java
├── planner
│   ├── IndexScanPlan.java
│   ├── Plan.java
│   ├── QueryPlanner.java
│   └── SeqScanPlan.java
├── server
│   └── MiniPostgresHttpServer.java
├── sql
│   ├── Condition.java
│   ├── Operator.java
│   ├── SqlParser.java
│   └── statement
│       └── ... statement classes ...
├── storage
│   ├── Row.java
│   ├── RowCodec.java
│   ├── Table.java
│   └── WriteAheadLog.java
├── tx
│   ├── Transaction.java
│   └── TransactionManager.java
└── util
    ├── Encoding.java
    ├── JsonUtil.java
    └── StringUtil.java
```

---

## 11. Write path

```text
Client SQL INSERT/UPDATE/DELETE
    ↓
SqlParser parses statement
    ↓
QueryEngine dispatches statement
    ↓
TransactionManager opens autocommit transaction if needed
    ↓
WriteAheadLog appends BEGIN / SQL / COMMIT
    ↓
Table mutates rows in memory
    ↓
Transaction undo action is registered
    ↓
Index entries are updated
    ↓
Table heap file is persisted on commit
```

---

## 12. Read path

```text
Client SQL SELECT
    ↓
SqlParser parses SELECT
    ↓
QueryPlanner inspects WHERE condition
    ↓
If indexed predicate exists:
      create IndexScanPlan
   else:
      create SeqScanPlan
    ↓
Table selects candidate rows
    ↓
Apply remaining filters
    ↓
Projection, ORDER BY, LIMIT
    ↓
Return QueryResult
```

---

## 13. Storage layout

```text
data/
├── catalog.meta
├── tables
│   ├── users.heap
│   └── orders.heap
└── wal
    └── mini-pg.wal
```

Each heap file stores one row per line with encoded key/value pairs and row metadata.

Example conceptual row:

```text
rowId=1 | deleted=false | createdTx=2 | deletedTx=0 | id=1 | name=Suhas | age=35
```

---

## 14. Important limitations

This project intentionally avoids many real PostgreSQL features:

- No PostgreSQL wire protocol
- No cost-based optimizer
- No joins
- No SQL grammar beyond the included subset
- No buffer pool
- No page layout
- No vacuum process
- No background checkpointer
- No isolation levels
- No lock manager
- No concurrent transaction conflict detection
- No crash replay of partially committed WAL records
- No primary keys or foreign keys
- No secondary storage format compatible with PostgreSQL
- No authentication or authorization

---

## 15. Clean generated data

```bash
rm -rf data build
```
