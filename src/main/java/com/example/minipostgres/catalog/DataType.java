package com.example.minipostgres.catalog;

public enum DataType {
    INT, LONG, DOUBLE, BOOLEAN, TEXT;

    public static DataType fromSql(String value) {
        String v = value.trim().toUpperCase();
        return switch (v) {
            case "INT", "INTEGER" -> INT;
            case "LONG", "BIGINT" -> LONG;
            case "DOUBLE", "FLOAT", "REAL" -> DOUBLE;
            case "BOOL", "BOOLEAN" -> BOOLEAN;
            case "TEXT", "VARCHAR", "STRING" -> TEXT;
            default -> throw new IllegalArgumentException("Unsupported data type: " + value);
        };
    }

    public Object parseLiteral(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.equalsIgnoreCase("null")) return null;
        if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
            v = v.substring(1, v.length() - 1).replace("''", "'");
        }
        return switch (this) {
            case INT -> Integer.parseInt(v);
            case LONG -> Long.parseLong(v);
            case DOUBLE -> Double.parseDouble(v);
            case BOOLEAN -> Boolean.parseBoolean(v);
            case TEXT -> v;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public int compare(Object left, Object right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        return ((Comparable) left).compareTo(right);
    }
}
