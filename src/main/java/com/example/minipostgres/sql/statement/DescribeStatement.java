package com.example.minipostgres.sql.statement;

public record DescribeStatement(String table) implements Statement {
    public StatementType type() { return StatementType.DESCRIBE; }
}
