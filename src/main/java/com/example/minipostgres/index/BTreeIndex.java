package com.example.minipostgres.index;

import com.example.minipostgres.catalog.DataType;
import com.example.minipostgres.sql.Operator;
import java.util.*;

public final class BTreeIndex {
    private final String name;
    private final String table;
    private final String column;
    private final DataType type;
    private final NavigableMap<ValueKey, LinkedHashSet<Long>> tree = new TreeMap<>();

    public BTreeIndex(String name, String table, String column, DataType type) {
        this.name = name.toLowerCase();
        this.table = table.toLowerCase();
        this.column = column.toLowerCase();
        this.type = type;
    }

    public String name() { return name; }
    public String table() { return table; }
    public String column() { return column; }

    public void add(Object value, long rowId) {
        tree.computeIfAbsent(new ValueKey(type, value), k -> new LinkedHashSet<>()).add(rowId);
    }

    public void remove(Object value, long rowId) {
        ValueKey key = new ValueKey(type, value);
        LinkedHashSet<Long> ids = tree.get(key);
        if (ids == null) return;
        ids.remove(rowId);
        if (ids.isEmpty()) tree.remove(key);
    }

    public List<Long> lookup(Operator operator, Object value) {
        ValueKey key = new ValueKey(type, value);
        NavigableMap<ValueKey, LinkedHashSet<Long>> view;
        switch (operator) {
            case EQ -> {
                LinkedHashSet<Long> set = tree.getOrDefault(key, new LinkedHashSet<>());
                return new ArrayList<>(set);
            }
            case GT -> view = tree.tailMap(key, false);
            case GTE -> view = tree.tailMap(key, true);
            case LT -> view = tree.headMap(key, false);
            case LTE -> view = tree.headMap(key, true);
            default -> { return List.of(); }
        }
        List<Long> ids = new ArrayList<>();
        for (LinkedHashSet<Long> set : view.values()) ids.addAll(set);
        return ids;
    }

    public int distinctKeys() { return tree.size(); }
}
