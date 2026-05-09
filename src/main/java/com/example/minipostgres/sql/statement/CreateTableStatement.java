package com.example.minipostgres.sql.statement;

import com.example.minipostgres.catalog.Column;
import java.util.*;

public record CreateTableStatement(String table, List<Column> columns) implements Statement {
    public StatementType type() { return StatementType.CREATE_TABLE; }
}
