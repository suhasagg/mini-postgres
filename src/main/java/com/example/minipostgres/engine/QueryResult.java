package com.example.minipostgres.engine;

import java.util.*;

public final class QueryResult {
    private final boolean success;
    private final String message;
    private final int updatedRows;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;

    public QueryResult(boolean success, String message, int updatedRows, List<String> columns, List<Map<String, Object>> rows) {
        this.success = success;
        this.message = message;
        this.updatedRows = updatedRows;
        this.columns = List.copyOf(columns);
        this.rows = List.copyOf(rows);
    }

    public static QueryResult ok(String message) { return new QueryResult(true, message, 0, List.of(), List.of()); }
    public static QueryResult update(String message, int count) { return new QueryResult(true, message, count, List.of(), List.of()); }
    public static QueryResult rows(List<String> columns, List<Map<String, Object>> rows) { return new QueryResult(true, "OK", rows.size(), columns, rows); }
    public static QueryResult error(String message) { return new QueryResult(false, message, 0, List.of(), List.of()); }

    public boolean success() { return success; }
    public String message() { return message; }
    public int updatedRows() { return updatedRows; }
    public List<String> columns() { return columns; }
    public List<Map<String, Object>> rows() { return rows; }

    public String toPrettyTable() {
        if (!success) return "ERROR: " + message;
        if (columns.isEmpty()) return message + (updatedRows > 0 ? " (" + updatedRows + " rows)" : "");
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(" | ", columns)).append('\n');
        sb.append("-".repeat(Math.max(3, sb.length() - 1))).append('\n');
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(" | ");
                sb.append(row.get(columns.get(i)));
            }
            sb.append('\n');
        }
        sb.append(rows.size()).append(" row(s)");
        return sb.toString();
    }
}
