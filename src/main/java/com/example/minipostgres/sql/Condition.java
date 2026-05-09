package com.example.minipostgres.sql;

import com.example.minipostgres.catalog.*;
import java.util.*;

public final class Condition {
    private final String column;
    private final Operator operator;
    private final String rawValue;

    public Condition(String column, Operator operator, String rawValue) {
        this.column = column.toLowerCase();
        this.operator = operator;
        this.rawValue = rawValue.trim();
    }

    public String column() { return column; }
    public Operator operator() { return operator; }
    public String rawValue() { return rawValue; }

    public Object typedValue(TableMetadata metadata) {
        return metadata.column(column).type().parseLiteral(rawValue);
    }

    public boolean matches(TableMetadata metadata, Map<String, Object> values) {
        Column c = metadata.column(column);
        Object left = values.get(column);
        Object right = c.type().parseLiteral(rawValue);
        int cmp = c.type().compare(left, right);
        return switch (operator) {
            case EQ -> cmp == 0;
            case NEQ -> cmp != 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
        };
    }

    @Override
    public String toString() { return column + " " + operator.symbol() + " " + rawValue; }
}
