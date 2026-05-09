package com.example.minipostgres;

import com.example.minipostgres.engine.*;
import java.nio.file.Path;

public final class IndexPlannerTest {
    public static void run() throws Exception {
        Path dir = TestSupport.tempDir("minipg-index");
        try (MiniPostgresDatabase db = new MiniPostgresDatabase(dir)) {
            db.execute("CREATE TABLE users (id INT, name TEXT, age INT);");
            db.execute("INSERT INTO users (id, name, age) VALUES (1, 'Suhas', 35);");
            db.execute("INSERT INTO users (id, name, age) VALUES (2, 'Alice', 31);");
            db.execute("CREATE INDEX idx_users_id ON users(id);");
            QueryResult plan = db.execute("EXPLAIN SELECT * FROM users WHERE id = 1;");
            TestSupport.assertTrue(String.valueOf(plan.rows().get(0).get("plan")).contains("IndexScan"), "uses index scan");
            QueryResult r = db.execute("SELECT name FROM users WHERE id = 2;");
            TestSupport.assertEquals("Alice", r.rows().get(0).get("name"), "index lookup works");
        }
    }
}
