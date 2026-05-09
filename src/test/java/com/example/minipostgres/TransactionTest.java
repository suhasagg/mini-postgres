package com.example.minipostgres;

import com.example.minipostgres.engine.*;
import java.nio.file.Path;

public final class TransactionTest {
    public static void run() throws Exception {
        Path dir = TestSupport.tempDir("minipg-tx");
        try (MiniPostgresDatabase db = new MiniPostgresDatabase(dir)) {
            Session s = new Session();
            db.execute(s, "CREATE TABLE users (id INT, name TEXT);");
            db.execute(s, "BEGIN;");
            db.execute(s, "INSERT INTO users (id, name) VALUES (1, 'Rollback');");
            QueryResult before = db.execute(s, "SELECT * FROM users WHERE id = 1;");
            TestSupport.assertEquals(1, before.rows().size(), "own write visible");
            db.execute(s, "ROLLBACK;");
            QueryResult after = db.execute(s, "SELECT * FROM users WHERE id = 1;");
            TestSupport.assertEquals(0, after.rows().size(), "rollback removed row");
            db.execute(s, "BEGIN;");
            db.execute(s, "INSERT INTO users (id, name) VALUES (2, 'Commit');");
            db.execute(s, "COMMIT;");
            QueryResult committed = db.execute(s, "SELECT name FROM users WHERE id = 2;");
            TestSupport.assertEquals("Commit", committed.rows().get(0).get("name"), "commit persists row");
        }
    }
}
