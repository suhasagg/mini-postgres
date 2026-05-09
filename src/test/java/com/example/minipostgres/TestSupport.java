package com.example.minipostgres;

import java.nio.file.*;
import java.io.IOException;

public final class TestSupport {
    private TestSupport() {}

    public static Path tempDir(String name) {
        try {
            Path dir = Files.createTempDirectory(name);
            return dir;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
