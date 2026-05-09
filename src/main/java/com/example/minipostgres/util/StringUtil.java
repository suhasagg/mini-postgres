package com.example.minipostgres.util;

import java.util.*;

public final class StringUtil {
    private StringUtil() {}

    public static List<String> splitCommaRespectingQuotes(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' ) {
                current.append(c);
                if (i + 1 < input.length() && input.charAt(i + 1) == '\'') {
                    current.append(input.charAt(++i));
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || input.endsWith(",")) result.add(current.toString().trim());
        return result;
    }

    public static String stripTrailingSemicolon(String sql) {
        String s = sql.trim();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    public static String unquoteIdentifier(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length()-1);
        return s;
    }
}
