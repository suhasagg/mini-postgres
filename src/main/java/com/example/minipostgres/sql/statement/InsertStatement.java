package com.example.minipostgres.sql.statement;

import java.util.*;

public record InsertStatement(String table, List<String> columns, List<String> values, String originalSql) implements Statement {
    public StatementType type() { return StatementType.INSERT; }
}
