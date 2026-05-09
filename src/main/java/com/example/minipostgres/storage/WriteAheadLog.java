package com.example.minipostgres.storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;

public final class WriteAheadLog implements Closeable {
    private final Path file;
    private final BufferedWriter writer;

    public WriteAheadLog(Path dataDir) {
        try {
            Path dir = dataDir.resolve("wal");
            Files.createDirectories(dir);
            this.file = dir.resolve("mini-pg.wal");
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open WAL", e);
        }
    }

    public synchronized void append(long txId, String type, String payload) {
        try {
            writer.write(Instant.now() + "|tx=" + txId + "|" + type + "|" + payload.replace("\n", " "));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to append WAL", e);
        }
    }

    public Path file() { return file; }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
