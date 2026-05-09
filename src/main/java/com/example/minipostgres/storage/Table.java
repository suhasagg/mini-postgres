package com.example.minipostgres.storage;

import com.example.minipostgres.catalog.*;
import com.example.minipostgres.index.BTreeIndex;
import com.example.minipostgres.planner.Plan;
import com.example.minipostgres.sql.Condition;
import com.example.minipostgres.tx.Transaction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Table {
    private final TableMetadata metadata;
    private final Path file;
    private final Map<Long, Row> rows = new LinkedHashMap<>();
    private final Map<String, BTreeIndex> indexesByColumn = new LinkedHashMap<>();
    private long nextRowId = 1;

    public Table(TableMetadata metadata, Path dataDir) {
        this.metadata = metadata;
        try {
            Files.createDirectories(dataDir.resolve("tables"));
        } catch (IOException e) { throw new RuntimeException(e); }
        this.file = dataDir.resolve("tables").resolve(metadata.name() + ".heap");
        load();
    }

    public TableMetadata metadata() { return metadata; }
    public Collection<Row> rows() { return rows.values(); }

    public void addIndex(String indexName, String column) {
        Column c = metadata.column(column);
        BTreeIndex index = new BTreeIndex(indexName, metadata.name(), c.name(), c.type());
        for (Row row : rows.values()) {
            if (!row.deleted()) index.add(row.values().get(c.name()), row.rowId());
        }
        indexesByColumn.put(c.name(), index);
    }

    public Optional<BTreeIndex> indexOn(String column) {
        return Optional.ofNullable(indexesByColumn.get(column.toLowerCase()));
    }

    public Row insert(Map<String, Object> values, long txId, Transaction tx) {
        Map<String, Object> normalized = normalize(values);
        Row row = new Row(nextRowId++, normalized, false, txId, 0);
        rows.put(row.rowId(), row);
        indexRow(row);
        tx.addUndo(() -> {
            unindexRow(row);
            rows.remove(row.rowId());
        });
        return row;
    }

    public int update(List<Condition> conditions, Map<String, Object> assignments, long txId, Transaction tx) {
        int count = 0;
        for (Row row : new ArrayList<>(rows.values())) {
            if (row.deleted() || !matches(row, conditions)) continue;
            Map<String, Object> before = new LinkedHashMap<>(row.values());
            unindexRow(row);
            for (Map.Entry<String, Object> e : assignments.entrySet()) row.values().put(e.getKey(), e.getValue());
            indexRow(row);
            tx.addUndo(() -> {
                unindexRow(row);
                row.values().clear();
                row.values().putAll(before);
                indexRow(row);
            });
            count++;
        }
        return count;
    }

    public int delete(List<Condition> conditions, long txId, Transaction tx) {
        int count = 0;
        for (Row row : new ArrayList<>(rows.values())) {
            if (row.deleted() || !matches(row, conditions)) continue;
            unindexRow(row);
            row.markDeleted(txId);
            tx.addUndo(() -> {
                row.restore();
                indexRow(row);
            });
            count++;
        }
        return count;
    }

    public List<Map<String, Object>> select(Plan plan, List<Condition> conditions, List<String> projections, Optional<String> orderBy, Optional<Integer> limit) {
        List<Row> candidates = new ArrayList<>();
        for (Long id : plan.candidateRowIds(this)) {
            Row row = rows.get(id);
            if (row != null && !row.deleted() && matches(row, conditions)) candidates.add(row);
        }
        if (orderBy.isPresent()) {
            String col = orderBy.get().toLowerCase();
            metadata.column(col);
            candidates.sort((a, b) -> metadata.column(col).type().compare(a.values().get(col), b.values().get(col)));
        }
        int max = limit.orElse(Integer.MAX_VALUE);
        List<String> selectedColumns = expandProjection(projections);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Row row : candidates) {
            if (out.size() >= max) break;
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String c : selectedColumns) projected.put(c, row.values().get(c));
            out.add(projected);
        }
        return out;
    }

    public List<String> expandProjection(List<String> projections) {
        if (projections.size() == 1 && projections.get(0).equals("*")) {
            return metadata.columns().stream().map(Column::name).toList();
        }
        List<String> out = new ArrayList<>();
        for (String p : projections) {
            metadata.column(p);
            out.add(p.toLowerCase());
        }
        return out;
    }

    public void persist() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (Row row : rows.values()) {
                    w.write(RowCodec.encode(row, metadata));
                    w.newLine();
                }
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist table " + metadata.name(), e);
        }
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                Row row = RowCodec.decode(line, metadata);
                rows.put(row.rowId(), row);
                nextRowId = Math.max(nextRowId, row.rowId() + 1);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load table " + metadata.name(), e);
        }
    }

    private boolean matches(Row row, List<Condition> conditions) {
        for (Condition c : conditions) if (!c.matches(metadata, row.values())) return false;
        return true;
    }

    private Map<String, Object> normalize(Map<String, Object> input) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Column column : metadata.columns()) {
            out.put(column.name(), input.get(column.name()));
        }
        return out;
    }

    private void indexRow(Row row) {
        if (row.deleted()) return;
        for (BTreeIndex index : indexesByColumn.values()) index.add(row.values().get(index.column()), row.rowId());
    }

    private void unindexRow(Row row) {
        if (row.deleted()) return;
        for (BTreeIndex index : indexesByColumn.values()) index.remove(row.values().get(index.column()), row.rowId());
    }

    public Path file() { return file; }
}
