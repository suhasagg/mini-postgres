# Mini Postgres Architecture

## Goal

Mini Postgres is an interview-friendly relational database built in plain Java. It is designed to show how the major pieces of PostgreSQL-like systems fit together without implementing PostgreSQL's full grammar, wire protocol, concurrency model, or page storage engine.

## Component responsibilities

| Component | Responsibility |
|---|---|
| `SqlParser` | Converts a small SQL subset into statement objects. |
| `QueryEngine` | Executes statements, controls autocommit behavior, and returns tabular results. |
| `Catalog` | Owns schema metadata and table/index definitions. |
| `Table` | Owns rows, heap persistence, filtering, projection, updates, and deletes. |
| `WriteAheadLog` | Appends transaction lifecycle and SQL statements before durable mutations. |
| `TransactionManager` | Allocates transaction ids and tracks the active transaction for a session. |
| `Transaction` | Stores undo actions for rollback. |
| `BTreeIndex` | Maintains sorted mapping from column value to matching row ids. |
| `QueryPlanner` | Chooses between sequential scan and index scan. |
| `MiniPostgresHttpServer` | Provides a small `/sql` endpoint for demos. |

## SQL frontend

The parser is intentionally simple and regex/token based. It supports a focused SQL subset:

- DDL: `CREATE TABLE`, `CREATE INDEX`
- DML: `INSERT`, `SELECT`, `UPDATE`, `DELETE`
- Transaction control: `BEGIN`, `COMMIT`, `ROLLBACK`
- Introspection: `SHOW TABLES`, `DESCRIBE table`, `EXPLAIN SELECT ...`

Real PostgreSQL uses a full parser, analyzer, rewriter, and planner. Mini Postgres compresses those phases into `SqlParser`, `QueryPlanner`, and `QueryEngine`.

## Catalog

The catalog stores table metadata:

- table name
- columns
- data types
- index definitions

Metadata is persisted into `catalog.meta` so the database can restart with schema and index definitions intact.

## Heap storage

Each table is stored as a heap-like file under `data/tables/<table>.heap`. Rows are not sorted by primary key. Each row has:

- `rowId`
- `deleted` marker
- `createdTx`
- `deletedTx`
- typed column values

This is conceptually similar to PostgreSQL heap tuples, but simplified heavily. There are no fixed-size pages, tuple headers, visibility maps, free-space maps, or TOAST storage.

## WAL

The WAL records transaction boundaries and mutating SQL statements:

```text
BEGIN tx=1
SQL tx=1 INSERT INTO users ...
COMMIT tx=1
```

In this educational implementation, committed table files are persisted directly at commit time, while WAL is kept for observability and architectural correctness. A real database would rely on WAL replay to recover from crashes between page flushes.

## Transactions

Autocommit mode wraps each mutating statement in a transaction:

```text
BEGIN -> execute statement -> COMMIT
```

Explicit transactions work through:

```sql
BEGIN;
... statements ...
COMMIT;
```

Rollback works by replaying undo actions captured during mutation. This is easier to understand than PostgreSQL's real MVCC implementation, but demonstrates the transaction lifecycle.

## MVCC-inspired metadata

Rows contain created and deleted transaction ids. This mirrors the idea of PostgreSQL's `xmin` and `xmax`, but this implementation does not enforce real snapshot isolation. It uses the metadata for learning, debugging, and explainability.

## Indexing

`BTreeIndex` is backed by `TreeMap<ValueKey, Set<RowId>>`, which gives sorted key lookup. It supports:

- equality lookup
- range lookup
- insert maintenance
- delete maintenance
- update maintenance through remove + add

A real PostgreSQL B-tree index is page-based and durable. This implementation rebuilds indexes from heap files on startup using catalog metadata.

## Planning

`QueryPlanner` chooses:

- `IndexScanPlan` when a WHERE predicate uses an indexed column
- `SeqScanPlan` otherwise

Examples:

```sql
EXPLAIN SELECT * FROM users WHERE id = 1;
```

Can return:

```text
IndexScan(table=users, index=idx_users_id, condition=id = 1)
```

While:

```sql
EXPLAIN SELECT * FROM users WHERE city = 'Delhi';
```

returns:

```text
SeqScan(table=users)
```

if `city` has no index.

## Execution

The executor follows this sequence:

1. Get candidate row ids from the plan.
2. Fetch rows from table storage.
3. Apply all WHERE predicates.
4. Project requested columns.
5. Sort by `ORDER BY` if present.
6. Apply `LIMIT`.
7. Return `QueryResult`.

## HTTP API

The HTTP API is intentionally small:

- `GET /health`
- `POST /sql`

The `/sql` endpoint accepts raw SQL text and returns JSON.

## Failure model

The project supports restart persistence for committed data and catalog metadata. It does not implement full crash recovery semantics for partially flushed data files.

## Design tradeoffs

| Decision | Why |
|---|---|
| Dependency-free Java | Easy to compile in interviews and sandboxes. |
| Regex/token parser | Keeps code understandable. |
| Row-per-line heap file | Makes persisted data inspectable. |
| TreeMap index | Demonstrates B-tree behavior without page implementation. |
| Undo-log rollback | Simpler than full MVCC snapshots. |
| HTTP endpoint | Easy to demo with curl. |

## Possible extensions

- Add primary key constraints
- Add joins
- Add GROUP BY and aggregation
- Add durable page format
- Add buffer pool
- Add lock manager
- Add snapshot isolation
- Add WAL replay recovery
- Add cost-based optimizer
- Add network protocol and JDBC driver
