package com.example.minipostgres.planner;

import com.example.minipostgres.index.BTreeIndex;
import com.example.minipostgres.sql.Condition;
import com.example.minipostgres.storage.Table;
import java.util.*;

public final class IndexScanPlan implements Plan {
    private final BTreeIndex index;
    private final Condition condition;
    private final Object typedValue;

    public IndexScanPlan(BTreeIndex index, Condition condition, Object typedValue) {
        this.index = index;
        this.condition = condition;
        this.typedValue = typedValue;
    }

    public String description() {
        return "IndexScan(table=" + index.table() + ", index=" + index.name() + ", condition=" + condition + ")";
    }

    public List<Long> candidateRowIds(Table table) {
        return index.lookup(condition.operator(), typedValue);
    }
}
