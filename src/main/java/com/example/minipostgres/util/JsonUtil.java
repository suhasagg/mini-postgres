package com.example.minipostgres.util;

import com.example.minipostgres.engine.QueryResult;
import java.util.*;

public final class JsonUtil {
    private JsonUtil() {}

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String value(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    public static String result(QueryResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"success\": ").append(result.success()).append(",\n");
        sb.append("  \"message\": \"").append(escape(result.message())).append("\",\n");
        sb.append("  \"updatedRows\": ").append(result.updatedRows()).append(",\n");
        sb.append("  \"columns\": [");
        for (int i = 0; i < result.columns().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(escape(result.columns().get(i))).append("\"");
        }
        sb.append("],\n");
        sb.append("  \"rows\": [\n");
        for (int r = 0; r < result.rows().size(); r++) {
            if (r > 0) sb.append(",\n");
            Map<String, Object> row = result.rows().get(r);
            sb.append("    {");
            int c = 0;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (c++ > 0) sb.append(", ");
                sb.append("\"").append(escape(e.getKey())).append("\": ").append(value(e.getValue()));
            }
            sb.append("}");
        }
        sb.append("\n  ]\n");
        sb.append("}");
        return sb.toString();
    }
}
