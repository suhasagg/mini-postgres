package com.example.minipostgres.sql.statement;

public record CreateIndexStatement(String indexName, String table, String column) implements Statement {
    public StatementType type() { return StatementType.CREATE_INDEX; }
}
