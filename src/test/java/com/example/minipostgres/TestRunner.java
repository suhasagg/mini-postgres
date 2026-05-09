package com.example.minipostgres;

public final class TestRunner {
    public static void main(String[] args) throws Exception {
        run("SqlParserTest", SqlParserTest::run);
        run("QueryEngineTest", QueryEngineTest::run);
        run("TransactionTest", TransactionTest::run);
        run("IndexPlannerTest", IndexPlannerTest::run);
        run("PersistenceTest", PersistenceTest::run);
        System.out.println("All tests passed.");
    }

    private static void run(String name, ThrowingRunnable r) throws Exception {
        r.run();
        System.out.println("[PASS] " + name);
    }

    @FunctionalInterface
    interface ThrowingRunnable { void run() throws Exception; }
}
