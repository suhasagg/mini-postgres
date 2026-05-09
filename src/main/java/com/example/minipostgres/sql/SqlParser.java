package com.example.minipostgres.sql;

import com.example.minipostgres.catalog.*;
import com.example.minipostgres.sql.statement.*;
import com.example.minipostgres.util.StringUtil;

import java.util.*;
import java.util.regex.*;

public final class SqlParser {
    private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^CREATE\\s+TABLE\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\((.*)\\)$");
    private static final Pattern CREATE_INDEX = Pattern.compile("(?is)^CREATE\\s+INDEX\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+ON\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\)$");
    private static final Pattern INSERT = Pattern.compile("(?is)^INSERT\\s+INTO\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\((.*?)\\)\\s*VALUES\\s*\\((.*)\\)$");
    private static final Pattern UPDATE = Pattern.compile("(?is)^UPDATE\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+SET\\s+(.*?)(?:\\s+WHERE\\s+(.*))?$");
    private static final Pattern DELETE = Pattern.compile("(?is)^DELETE\\s+FROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\\s+WHERE\\s+(.*))?$");
    private static final Pattern DESCRIBE = Pattern.compile("(?is)^(?:DESCRIBE|DESC)\\s+([a-zA-Z_][a-zA-Z0-9_]*)$");

    public Statement parse(String sql) {
        String original = StringUtil.stripTrailingSemicolon(sql);
        String s = original.trim();
        String upper = s.toUpperCase(Locale.ROOT);
        if (upper.equals("BEGIN")) return new SimpleStatement(StatementType.BEGIN);
        if (upper.equals("COMMIT")) return new SimpleStatement(StatementType.COMMIT);
        if (upper.equals("ROLLBACK")) return new SimpleStatement(StatementType.ROLLBACK);
        if (upper.equals("SHOW TABLES")) return new SimpleStatement(StatementType.SHOW_TABLES);
        if (upper.startsWith("EXPLAIN ")) return new ExplainStatement(parseSelect(s.substring("EXPLAIN".length()).trim()));

        Matcher m;
        m = CREATE_TABLE.matcher(s);
        if (m.matches()) return parseCreateTable(m);
        m = CREATE_INDEX.matcher(s);
        if (m.matches()) return new CreateIndexStatement(m.group(1).toLowerCase(), m.group(2).toLowerCase(), m.group(3).toLowerCase());
        m = INSERT.matcher(s);
        if (m.matches()) return parseInsert(m, original);
        if (upper.startsWith("SELECT ")) return parseSelect(s);
        m = UPDATE.matcher(s);
        if (m.matches()) return parseUpdate(m, original);
        m = DELETE.matcher(s);
        if (m.matches()) return parseDelete(m, original);
        m = DESCRIBE.matcher(s);
        if (m.matches()) return new DescribeStatement(m.group(1).toLowerCase());
        throw new IllegalArgumentException("Unsupported SQL: " + sql);
    }

    private Statement parseCreateTable(Matcher m) {
        String table = m.group(1).toLowerCase();
        String body = m.group(2);
        List<Column> columns = new ArrayList<>();
        for (String part : StringUtil.splitCommaRespectingQuotes(body)) {
            String[] tokens = part.trim().split("\\s+");
            if (tokens.length < 2) throw new IllegalArgumentException("Invalid column definition: " + part);
            columns.add(new Column(tokens[0].toLowerCase(), DataType.fromSql(tokens[1])));
        }
        return new CreateTableStatement(table, columns);
    }

    private Statement parseInsert(Matcher m, String original) {
        String table = m.group(1).toLowerCase();
        List<String> columns = new ArrayList<>();
        for (String c : StringUtil.splitCommaRespectingQuotes(m.group(2))) columns.add(c.toLowerCase());
        List<String> values = StringUtil.splitCommaRespectingQuotes(m.group(3));
        if (columns.size() != values.size()) throw new IllegalArgumentException("Column count does not match value count");
        return new InsertStatement(table, columns, values, original);
    }

    private SelectStatement parseSelect(String s) {
        String upper = s.toUpperCase(Locale.ROOT);
        int fromIdx = indexOfKeywordOutsideQuotes(upper, " FROM ", 0);
        if (fromIdx < 0) throw new IllegalArgumentException("SELECT must include FROM");
        String projectionPart = s.substring("SELECT".length(), fromIdx).trim();
        String rest = s.substring(fromIdx + 6).trim();

        String table;
        String wherePart = null;
        String orderBy = null;
        Integer limit = null;

        int whereIdx = indexOfKeywordOutsideQuotes(rest.toUpperCase(Locale.ROOT), " WHERE ", 0);
        int orderIdx = indexOfKeywordOutsideQuotes(rest.toUpperCase(Locale.ROOT), " ORDER BY ", 0);
        int limitIdx = indexOfKeywordOutsideQuotes(rest.toUpperCase(Locale.ROOT), " LIMIT ", 0);
        int endTable = minPositive(whereIdx, orderIdx, limitIdx, rest.length());
        table = rest.substring(0, endTable).trim().toLowerCase();

        if (whereIdx >= 0) {
            int endWhere = minPositive(orderIdx > whereIdx ? orderIdx : -1, limitIdx > whereIdx ? limitIdx : -1, -1, rest.length());
            wherePart = rest.substring(whereIdx + 7, endWhere).trim();
        }
        if (orderIdx >= 0) {
            int endOrder = minPositive(limitIdx > orderIdx ? limitIdx : -1, rest.length());
            orderBy = rest.substring(orderIdx + 10, endOrder).trim().toLowerCase();
        }
        if (limitIdx >= 0) {
            String limitPart = rest.substring(limitIdx + 7).trim();
            limit = Integer.parseInt(limitPart);
        }

        List<String> projections = new ArrayList<>();
        for (String p : StringUtil.splitCommaRespectingQuotes(projectionPart)) projections.add(p.trim().equals("*") ? "*" : p.trim().toLowerCase());
        List<Condition> conditions = parseConditions(wherePart);
        return new SelectStatement(table, projections, conditions, Optional.ofNullable(orderBy), Optional.ofNullable(limit));
    }

    private Statement parseUpdate(Matcher m, String original) {
        String table = m.group(1).toLowerCase();
        Map<String, String> assignments = new LinkedHashMap<>();
        for (String assignment : StringUtil.splitCommaRespectingQuotes(m.group(2))) {
            int eq = assignment.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("Invalid assignment: " + assignment);
            assignments.put(assignment.substring(0, eq).trim().toLowerCase(), assignment.substring(eq + 1).trim());
        }
        return new UpdateStatement(table, assignments, parseConditions(m.group(3)), original);
    }

    private Statement parseDelete(Matcher m, String original) {
        return new DeleteStatement(m.group(1).toLowerCase(), parseConditions(m.group(2)), original);
    }

    private List<Condition> parseConditions(String wherePart) {
        if (wherePart == null || wherePart.isBlank()) return List.of();
        List<Condition> conditions = new ArrayList<>();
        for (String part : wherePart.split("(?i)\\s+AND\\s+")) {
            Matcher m = Pattern.compile("(?is)^([a-zA-Z_][a-zA-Z0-9_]*)\\s*(>=|<=|!=|<>|=|>|<)\\s*(.+)$").matcher(part.trim());
            if (!m.matches()) throw new IllegalArgumentException("Invalid WHERE condition: " + part);
            conditions.add(new Condition(m.group(1).toLowerCase(), Operator.fromSymbol(m.group(2)), m.group(3).trim()));
        }
        return conditions;
    }

    private int indexOfKeywordOutsideQuotes(String haystack, String needle, int start) {
        boolean inQuote = false;
        for (int i = start; i <= haystack.length() - needle.length(); i++) {
            char c = haystack.charAt(i);
            if (c == '\'') inQuote = !inQuote;
            if (!inQuote && haystack.startsWith(needle, i)) return i;
        }
        return -1;
    }

    private int minPositive(int a, int b, int c, int fallback) {
        int min = fallback;
        if (a >= 0) min = Math.min(min, a);
        if (b >= 0) min = Math.min(min, b);
        if (c >= 0) min = Math.min(min, c);
        return min;
    }
    private int minPositive(int a, int b) {
        int min = b;
        if (a >= 0) min = Math.min(min, a);
        return min;
    }
}
