package com.example.minipostgres.sql;

public enum Operator {
    EQ("="), NEQ("!="), GT(">"), GTE(">="), LT("<"), LTE("<=");

    private final String symbol;
    Operator(String symbol) { this.symbol = symbol; }
    public String symbol() { return symbol; }

    public static Operator fromSymbol(String s) {
        return switch (s.trim()) {
            case "=" -> EQ;
            case "!=", "<>" -> NEQ;
            case ">" -> GT;
            case ">=" -> GTE;
            case "<" -> LT;
            case "<=" -> LTE;
            default -> throw new IllegalArgumentException("Unsupported operator: " + s);
        };
    }
}
