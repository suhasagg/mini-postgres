package com.example.minipostgres.engine;

import com.example.minipostgres.catalog.*;
import com.example.minipostgres.planner.*;
import com.example.minipostgres.sql.SqlParser;
import com.example.minipostgres.sql.statement.*;
import com.example.minipostgres.storage.*;
import com.example.minipostgres.tx.*;

import java.util.*;

public final class QueryEngine {
    private final Catalog catalog;
    private final WriteAheadLog wal;
    private final TransactionManager txManager;
    private final SqlParser parser = new SqlParser();
    private final QueryPlanner planner = new QueryPlanner();

    public QueryEngine(Catalog catalog, WriteAheadLog wal, TransactionManager txManager) {
        this.catalog = catalog;
        this.wal = wal;
        this.txManager = txManager;
    }

    public synchronized QueryResult execute(Session session, String sql) {
        try {
            Statement statement = parser.parse(sql);
            return switch (statement.type()) {
                case BEGIN -> begin(session);
                case COMMIT -> commit(session);
                case ROLLBACK -> rollback(session);
                case CREATE_TABLE -> withAutocommit(session, sql, tx -> createTable((CreateTableStatement) statement));
                case CREATE_INDEX -> withAutocommit(session, sql, tx -> createIndex((CreateIndexStatement) statement));
                case INSERT -> withAutocommit(session, sql, tx -> insert((InsertStatement) statement, tx));
                case UPDATE -> withAutocommit(session, sql, tx -> update((UpdateStatement) statement, tx));
                case DELETE -> withAutocommit(session, sql, tx -> delete((DeleteStatement) statement, tx));
                case SELECT -> select((SelectStatement) statement);
                case SHOW_TABLES -> showTables();
                case DESCRIBE -> describe((DescribeStatement) statement);
                case EXPLAIN -> explain((ExplainStatement) statement);
            };
        } catch (Exception e) {
            return QueryResult.error(e.getMessage());
        }
    }

    private QueryResult begin(Session session) {
        if (session.inTransaction()) throw new IllegalStateException("Transaction already active");
        Transaction tx = txManager.begin();
        session.setTransaction(tx);
        wal.append(tx.id(), "BEGIN", "");
        return QueryResult.ok("BEGIN tx=" + tx.id());
    }

    private QueryResult commit(Session session) {
        if (!session.inTransaction()) throw new IllegalStateException("No active transaction");
        Transaction tx = session.transaction();
        catalog.persistTables();
        tx.commit();
        wal.append(tx.id(), "COMMIT", "");
        session.clearTransaction();
        return QueryResult.ok("COMMIT tx=" + tx.id());
    }

    private QueryResult rollback(Session session) {
        if (!session.inTransaction()) throw new IllegalStateException("No active transaction");
        Transaction tx = session.transaction();
        tx.rollback();
        catalog.persistTables();
        wal.append(tx.id(), "ROLLBACK", "");
        session.clearTransaction();
        return QueryResult.ok("ROLLBACK tx=" + tx.id());
    }

    private QueryResult withAutocommit(Session session, String originalSql, TxWork work) {
        if (session.inTransaction()) {
            wal.append(session.transaction().id(), "SQL", originalSql);
            return work.run(session.transaction());
        }
        Transaction tx = txManager.begin();
        try {
            wal.append(tx.id(), "BEGIN", "");
            wal.append(tx.id(), "SQL", originalSql);
            QueryResult result = work.run(tx);
            catalog.persistTables();
            tx.commit();
            wal.append(tx.id(), "COMMIT", "");
            return result;
        } catch (RuntimeException e) {
            tx.rollback();
            catalog.persistTables();
            wal.append(tx.id(), "ROLLBACK", e.getMessage() == null ? "" : e.getMessage());
            throw e;
        }
    }

    private QueryResult createTable(CreateTableStatement s) {
        catalog.createTable(s.table(), s.columns());
        return QueryResult.ok("CREATE TABLE " + s.table());
    }

    private QueryResult createIndex(CreateIndexStatement s) {
        catalog.createIndex(s.indexName(), s.table(), s.column());
        return QueryResult.ok("CREATE INDEX " + s.indexName());
    }

    private QueryResult insert(InsertStatement s, Transaction tx) {
        Table table = catalog.table(s.table());
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < s.columns().size(); i++) {
            String column = s.columns().get(i).toLowerCase();
            Column c = table.metadata().column(column);
            values.put(column, c.type().parseLiteral(s.values().get(i)));
        }
        table.insert(values, tx.id(), tx);
        return QueryResult.update("INSERT 1", 1);
    }

    private QueryResult update(UpdateStatement s, Transaction tx) {
        Table table = catalog.table(s.table());
        Map<String, Object> assignments = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : s.assignments().entrySet()) {
            Column c = table.metadata().column(e.getKey());
            assignments.put(c.name(), c.type().parseLiteral(e.getValue()));
        }
        int count = table.update(s.conditions(), assignments, tx.id(), tx);
        return QueryResult.update("UPDATE", count);
    }

    private QueryResult delete(DeleteStatement s, Transaction tx) {
        Table table = catalog.table(s.table());
        int count = table.delete(s.conditions(), tx.id(), tx);
        return QueryResult.update("DELETE", count);
    }

    private QueryResult select(SelectStatement s) {
        Table table = catalog.table(s.table());
        Plan plan = planner.plan(table, s.conditions());
        List<String> columns = table.expandProjection(s.projections());
        List<Map<String, Object>> rows = table.select(plan, s.conditions(), s.projections(), s.orderBy(), s.limit());
        return QueryResult.rows(columns, rows);
    }

    private QueryResult explain(ExplainStatement s) {
        Table table = catalog.table(s.select().table());
        Plan plan = planner.plan(table, s.select().conditions());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("plan", plan.description());
        return QueryResult.rows(List.of("plan"), List.of(row));
    }

    private QueryResult showTables() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String name : catalog.tableNames()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("table", name);
            rows.add(row);
        }
        return QueryResult.rows(List.of("table"), rows);
    }

    private QueryResult describe(DescribeStatement s) {
        TableMetadata tm = catalog.metadata(s.table());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Column c : tm.columns()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("column", c.name());
            row.put("type", c.type().name());
            row.put("index", tm.indexOnColumn(c.name()).orElse(""));
            rows.add(row);
        }
        return QueryResult.rows(List.of("column", "type", "index"), rows);
    }

    @FunctionalInterface
    private interface TxWork { QueryResult run(Transaction tx); }
}
