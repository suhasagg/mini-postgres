package com.example.minipostgres;

import com.example.minipostgres.sql.SqlParser;
import com.example.minipostgres.sql.statement.*;

public final class SqlParserTest {
    public static void run() {
        SqlParser p = new SqlParser();
        Statement s = p.parse("CREATE TABLE users (id INT, name TEXT);");
        TestSupport.assertEquals(StatementType.CREATE_TABLE, s.type(), "create table type");
        SelectStatement select = (SelectStatement) p.parse("SELECT id, name FROM users WHERE id = 1 ORDER BY name LIMIT 5;");
        TestSupport.assertEquals("users", select.table(), "select table");
        TestSupport.assertEquals(2, select.projections().size(), "projection count");
        TestSupport.assertEquals(1, select.conditions().size(), "condition count");
        TestSupport.assertEquals("name", select.orderBy().orElseThrow(), "order by");
        TestSupport.assertEquals(5, select.limit().orElseThrow(), "limit");
    }
}
