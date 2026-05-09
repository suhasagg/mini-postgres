package com.example.minipostgres.catalog;

import java.util.*;

public final class TableMetadata {
    private final String name;
    private final List<Column> columns;
    private final Map<String, Column> byName = new LinkedHashMap<>();
    private final Map<String, String> indexes = new LinkedHashMap<>();

    public TableMetadata(String name, List<Column> columns) {
        this.name = name.toLowerCase();
        this.columns = List.copyOf(columns);
        for (Column column : columns) {
            byName.put(column.name(), column);
        }
    }

    public String name() { return name; }
    public List<Column> columns() { return columns; }

    public Column column(String name) {
        Column column = byName.get(name.toLowerCase());
        if (column == null) throw new IllegalArgumentException("Unknown column '" + name + "' on table '" + this.name + "'");
        return column;
    }

    public boolean hasColumn(String name) { return byName.containsKey(name.toLowerCase()); }

    public void addIndex(String indexName, String columnName) {
        column(columnName);
        indexes.put(indexName.toLowerCase(), columnName.toLowerCase());
    }

    public Map<String, String> indexes() { return Collections.unmodifiableMap(indexes); }

    public Optional<String> indexOnColumn(String columnName) {
        String c = columnName.toLowerCase();
        for (Map.Entry<String, String> e : indexes.entrySet()) {
            if (e.getValue().equals(c)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }
}
