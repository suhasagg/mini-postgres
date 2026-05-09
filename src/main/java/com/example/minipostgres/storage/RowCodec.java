package com.example.minipostgres.storage;

import com.example.minipostgres.catalog.*;
import com.example.minipostgres.util.Encoding;
import java.util.*;

public final class RowCodec {
    private RowCodec() {}

    public static String encode(Row row, TableMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append(row.rowId()).append('|').append(row.deleted()).append('|').append(row.createdTx()).append('|').append(row.deletedTx());
        for (Column c : metadata.columns()) {
            sb.append('|').append(Encoding.encode(c.name())).append('=');
            Object value = row.values().get(c.name());
            sb.append(Encoding.encode(value == null ? "__NULL__" : String.valueOf(value)));
        }
        return sb.toString();
    }

    public static Row decode(String line, TableMetadata metadata) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 4) throw new IllegalArgumentException("Bad row line: " + line);
        long rowId = Long.parseLong(parts[0]);
        boolean deleted = Boolean.parseBoolean(parts[1]);
        long createdTx = Long.parseLong(parts[2]);
        long deletedTx = Long.parseLong(parts[3]);
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 4; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq < 0) continue;
            String name = Encoding.decode(parts[i].substring(0, eq));
            String raw = Encoding.decode(parts[i].substring(eq + 1));
            Column c = metadata.column(name);
            values.put(name, "__NULL__".equals(raw) ? null : c.type().parseLiteral(raw));
        }
        return new Row(rowId, values, deleted, createdTx, deletedTx);
    }
}
