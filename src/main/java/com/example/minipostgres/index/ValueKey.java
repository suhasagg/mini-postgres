package com.example.minipostgres.index;

import com.example.minipostgres.catalog.DataType;

public final class ValueKey implements Comparable<ValueKey> {
    private final DataType type;
    private final Object value;

    public ValueKey(DataType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public Object value() { return value; }

    @Override
    public int compareTo(ValueKey other) {
        if (type != other.type) return type.name().compareTo(other.type.name());
        return type.compare(value, other.value);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ValueKey v)) return false;
        return type == v.type && compareTo(v) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + (value == null ? 0 : value.hashCode());
    }

    @Override
    public String toString() { return String.valueOf(value); }
}
