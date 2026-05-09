package com.example.minipostgres.sql.statement;

import com.example.minipostgres.sql.Condition;
import java.util.*;

public record UpdateStatement(String table, Map<String, String> assignments, List<Condition> conditions, String originalSql) implements Statement {
    public StatementType type() { return StatementType.UPDATE; }
}
