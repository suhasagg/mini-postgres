package com.example.minipostgres;

import com.example.minipostgres.engine.*;
import java.nio.file.Path;

public final class QueryEngineTest {
    public static void run() throws Exception {
        Path dir = TestSupport.tempDir("minipg-query");
        try (MiniPostgresDatabase db = new MiniPostgresDatabase(dir)) {
            TestSupport.assertTrue(db.execute("CREATE TABLE users (id INT, name TEXT, age INT);").success(), "create users");
            TestSupport.assertTrue(db.execute("INSERT INTO users (id, name, age) VALUES (1, 'Suhas', 35);").success(), "insert user");
            QueryResult r = db.execute("SELECT name FROM users WHERE id = 1;");
            TestSupport.assertEquals(1, r.rows().size(), "one row");
            TestSupport.assertEquals("Suhas", r.rows().get(0).get("name"), "selected name");
            db.execute("UPDATE users SET name = 'Alice' WHERE id = 1;");
            r = db.execute("SELECT name FROM users WHERE id = 1;");
            TestSupport.assertEquals("Alice", r.rows().get(0).get("name"), "updated name");
            db.execute("DELETE FROM users WHERE id = 1;");
            r = db.execute("SELECT * FROM users WHERE id = 1;");
            TestSupport.assertEquals(0, r.rows().size(), "deleted row hidden");
        }
    }
}
