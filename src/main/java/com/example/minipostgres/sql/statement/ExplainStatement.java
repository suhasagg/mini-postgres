package com.example.minipostgres.sql.statement;

public record ExplainStatement(SelectStatement select) implements Statement {
    public StatementType type() { return StatementType.EXPLAIN; }
}
