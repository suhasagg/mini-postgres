package com.example.minipostgres.sql.statement;

public enum StatementType {
    CREATE_TABLE, CREATE_INDEX, INSERT, SELECT, UPDATE, DELETE,
    BEGIN, COMMIT, ROLLBACK, SHOW_TABLES, DESCRIBE, EXPLAIN
}
