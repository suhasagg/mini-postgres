package com.example.minipostgres.sql.statement;

import com.example.minipostgres.sql.Condition;
import java.util.*;

public record SelectStatement(String table, List<String> projections, List<Condition> conditions,
                              Optional<String> orderBy, Optional<Integer> limit) implements Statement {
    public StatementType type() { return StatementType.SELECT; }
}
