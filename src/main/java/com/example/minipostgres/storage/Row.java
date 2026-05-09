package com.example.minipostgres.storage;

import java.util.*;

public final class Row {
    private final long rowId;
    private final Map<String, Object> values;
    private boolean deleted;
    private final long createdTx;
    private long deletedTx;

    public Row(long rowId, Map<String, Object> values, boolean deleted, long createdTx, long deletedTx) {
        this.rowId = rowId;
        this.values = new LinkedHashMap<>(values);
        this.deleted = deleted;
        this.createdTx = createdTx;
        this.deletedTx = deletedTx;
    }

    public long rowId() { return rowId; }
    public Map<String, Object> values() { return values; }
    public boolean deleted() { return deleted; }
    public long createdTx() { return createdTx; }
    public long deletedTx() { return deletedTx; }

    public void markDeleted(long txId) { this.deleted = true; this.deletedTx = txId; }
    public void restore() { this.deleted = false; this.deletedTx = 0; }

    public Row copy() {
        return new Row(rowId, values, deleted, createdTx, deletedTx);
    }
}
