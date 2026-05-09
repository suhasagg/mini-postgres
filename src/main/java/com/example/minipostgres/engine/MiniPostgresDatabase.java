package com.example.minipostgres.engine;

import com.example.minipostgres.catalog.Catalog;
import com.example.minipostgres.storage.WriteAheadLog;
import com.example.minipostgres.tx.TransactionManager;

import java.io.IOException;
import java.nio.file.Path;

public final class MiniPostgresDatabase implements AutoCloseable {
    private final Path dataDir;
    private final Catalog catalog;
    private final WriteAheadLog wal;
    private final TransactionManager txManager;
    private final QueryEngine queryEngine;

    public MiniPostgresDatabase(Path dataDir) {
        this.dataDir = dataDir;
        this.catalog = new Catalog(dataDir);
        this.wal = new WriteAheadLog(dataDir);
        this.txManager = new TransactionManager();
        this.queryEngine = new QueryEngine(catalog, wal, txManager);
    }

    public QueryResult execute(Session session, String sql) {
        return queryEngine.execute(session, sql);
    }

    public QueryResult execute(String sql) {
        return execute(new Session(), sql);
    }

    public Catalog catalog() { return catalog; }
    public Path dataDir() { return dataDir; }

    @Override
    public void close() throws IOException {
        wal.close();
    }
}
