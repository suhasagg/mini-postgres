package com.example.minipostgres.planner;

import com.example.minipostgres.index.BTreeIndex;
import com.example.minipostgres.sql.*;
import com.example.minipostgres.storage.Table;
import java.util.*;

public final class QueryPlanner {
    public Plan plan(Table table, List<Condition> conditions) {
        for (Condition condition : conditions) {
            Optional<BTreeIndex> index = table.indexOn(condition.column());
            if (index.isPresent() && condition.operator() != Operator.NEQ) {
                Object typed = condition.typedValue(table.metadata());
                return new IndexScanPlan(index.get(), condition, typed);
            }
        }
        return new SeqScanPlan(table.metadata().name());
    }
}
