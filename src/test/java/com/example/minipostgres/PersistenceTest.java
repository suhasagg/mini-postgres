package com.example.minipostgres;

import com.example.minipostgres.engine.*;
import java.nio.file.Path;

public final class PersistenceTest {
    public static void run() throws Exception {
        Path dir = TestSupport.tempDir("minipg-persist");
        try (MiniPostgresDatabase db = new MiniPostgresDatabase(dir)) {
            db.execute("CREATE TABLE users (id INT, name TEXT);");
            db.execute("INSERT INTO users (id, name) VALUES (1, 'Persisted');");
            db.execute("CREATE INDEX idx_users_id ON users(id);");
        }
        try (MiniPostgresDatabase db2 = new MiniPostgresDatabase(dir)) {
            QueryResult r = db2.execute("SELECT name FROM users WHERE id = 1;");
            TestSupport.assertEquals(1, r.rows().size(), "row after restart");
            TestSupport.assertEquals("Persisted", r.rows().get(0).get("name"), "value after restart");
            QueryResult plan = db2.execute("EXPLAIN SELECT * FROM users WHERE id = 1;");
            TestSupport.assertTrue(String.valueOf(plan.rows().get(0).get("plan")).contains("IndexScan"), "index metadata after restart");
        }
    }
}
