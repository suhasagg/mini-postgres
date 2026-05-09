package com.example.minipostgres.sql.statement;

import com.example.minipostgres.sql.Condition;
import java.util.*;

public record DeleteStatement(String table, List<Condition> conditions, String originalSql) implements Statement {
    public StatementType type() { return StatementType.DELETE; }
}
