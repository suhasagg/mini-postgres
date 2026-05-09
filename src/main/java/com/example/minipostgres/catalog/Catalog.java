package com.example.minipostgres.catalog;

import com.example.minipostgres.storage.Table;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Catalog {
    private final Path dataDir;
    private final Path catalogFile;
    private final Map<String, TableMetadata> metadata = new LinkedHashMap<>();
    private final Map<String, Table> tables = new LinkedHashMap<>();

    public Catalog(Path dataDir) {
        this.dataDir = dataDir;
        this.catalogFile = dataDir.resolve("catalog.meta");
        try { Files.createDirectories(dataDir); } catch (IOException e) { throw new RuntimeException(e); }
        load();
    }

    public boolean hasTable(String name) { return metadata.containsKey(name.toLowerCase()); }

    public Table createTable(String name, List<Column> columns) {
        String tableName = name.toLowerCase();
        if (metadata.containsKey(tableName)) throw new IllegalArgumentException("Table already exists: " + tableName);
        TableMetadata tm = new TableMetadata(tableName, columns);
        metadata.put(tableName, tm);
        Table table = new Table(tm, dataDir);
        tables.put(tableName, table);
        persist();
        return table;
    }

    public void createIndex(String indexName, String tableName, String column) {
        Table table = table(tableName);
        table.metadata().addIndex(indexName, column);
        table.addIndex(indexName, column);
        persist();
    }

    public Table table(String name) {
        String n = name.toLowerCase();
        Table table = tables.get(n);
        if (table == null) throw new IllegalArgumentException("Unknown table: " + name);
        return table;
    }

    public TableMetadata metadata(String name) {
        TableMetadata tm = metadata.get(name.toLowerCase());
        if (tm == null) throw new IllegalArgumentException("Unknown table: " + name);
        return tm;
    }

    public Collection<Table> tables() { return tables.values(); }
    public Set<String> tableNames() { return metadata.keySet(); }

    public void persistTables() {
        for (Table t : tables.values()) t.persist();
    }

    public void persist() {
        try {
            Files.createDirectories(dataDir);
            Path tmp = catalogFile.resolveSibling("catalog.meta.tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (TableMetadata tm : metadata.values()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("TABLE|").append(tm.name()).append('|');
                    int i = 0;
                    for (Column c : tm.columns()) {
                        if (i++ > 0) sb.append(',');
                        sb.append(c.name()).append(':').append(c.type());
                    }
                    w.write(sb.toString());
                    w.newLine();
                    for (Map.Entry<String, String> e : tm.indexes().entrySet()) {
                        w.write("INDEX|" + tm.name() + "|" + e.getKey() + "|" + e.getValue());
                        w.newLine();
                    }
                }
            }
            Files.move(tmp, catalogFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist catalog", e);
        }
    }

    private void load() {
        if (!Files.exists(catalogFile)) return;
        List<String[]> pendingIndexes = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(catalogFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts[0].equals("TABLE")) {
                    String tableName = parts[1];
                    List<Column> columns = new ArrayList<>();
                    if (parts.length > 2 && !parts[2].isBlank()) {
                        for (String c : parts[2].split(",")) {
                            String[] kv = c.split(":");
                            columns.add(new Column(kv[0], DataType.fromSql(kv[1])));
                        }
                    }
                    TableMetadata tm = new TableMetadata(tableName, columns);
                    metadata.put(tableName, tm);
                } else if (parts[0].equals("INDEX")) {
                    pendingIndexes.add(parts);
                }
            }
            for (TableMetadata tm : metadata.values()) tables.put(tm.name(), new Table(tm, dataDir));
            for (String[] idx : pendingIndexes) {
                String tableName = idx[1], indexName = idx[2], column = idx[3];
                metadata(tableName).addIndex(indexName, column);
                table(tableName).addIndex(indexName, column);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load catalog", e);
        }
    }
}
