package com.example.minipostgres.catalog;

public final class Column {
    private final String name;
    private final DataType type;

    public Column(String name, DataType type) {
        this.name = name.toLowerCase();
        this.type = type;
    }

    public String name() { return name; }
    public DataType type() { return type; }

    @Override
    public String toString() {
        return name + " " + type;
    }
}
