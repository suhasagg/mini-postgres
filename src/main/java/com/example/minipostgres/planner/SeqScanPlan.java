package com.example.minipostgres.planner;

import com.example.minipostgres.storage.Table;
import java.util.*;

public final class SeqScanPlan implements Plan {
    private final String table;
    public SeqScanPlan(String table) { this.table = table; }
    public String description() { return "SeqScan(table=" + table + ")"; }
    public List<Long> candidateRowIds(Table table) {
        return table.rows().stream().map(r -> r.rowId()).toList();
    }
}
