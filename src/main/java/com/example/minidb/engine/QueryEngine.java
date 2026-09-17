package com.example.minidb.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.minidb.storage.RecordStorage;
import org.springframework.stereotype.Component;

@Component
public class QueryEngine {

    private static final String SELECT_PREFIX = "SELECT * FROM";

    // Matches "<column> <op> <value>" the way MySQL would let you write it:
    // no spaces required around the operator, and the longer two-character
    // operators (>=, <=, !=, <>) are tried before the single-character ones
    // so "age>=20" doesn't get misread as "age" ">" "=20". The column is
    // restricted to \w (letters/digits/underscore) — the same charset
    // column names are validated against elsewhere — so it can never
    // greedily swallow part of the operator the way a bare \S+ would for
    // symbols like "!=" (e.g. mis-splitting "dept!=CSE" as "dept!" "=" "CSE").
    private static final Pattern WHERE_PATTERN =
            Pattern.compile("^(\\w+)\\s*(>=|<=|!=|<>|=|>|<)\\s*(.+)$");

    private final RecordStorage recordStorage;

    public QueryEngine(RecordStorage recordStorage) {
        this.recordStorage = recordStorage;
    }

    public List<Map<String, Object>> selectAll(Path tablePath) throws IOException {
        return recordStorage.readRows(tablePath.resolve("data.dat"));
    }

    /**
     * Parses a small "SELECT * FROM <table> [WHERE <col> <op> <value>]"
     * query, written the way you'd write it in MySQL: standard comparison
     * operators (=, !=, <>, >, <, >=, <=), spaces around the operator are
     * optional, and string values can be single- or double-quoted
     * ('CSE' or "CSE") or bare (CSE). The table name is not hardcoded:
     * whatever table name appears in the query text is matched
     * (case-insensitively) against the table the caller resolved from the
     * URL, so this works for any table, not just "students".
     *
     * The query console always reads the table's live data.dat, never a
     * transaction's staging file, so uncommitted changes never show up here
     * and committed ones always do as soon as they land.
     *
     * Only a single condition is supported — no AND/OR, no nested clauses,
     * no joins. That's a deliberate scope limit of this teaching project,
     * not a bug.
     */
    public List<Map<String, Object>> parseSelectQuery(Path tablePath, String tableName, String query) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty.");
        }

        String normalized = query.trim().replaceAll("\\s+", " ");
        String upperNormalized = normalized.toUpperCase();

        if (!upperNormalized.startsWith(SELECT_PREFIX)) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }

        String remainder = normalized.substring(SELECT_PREFIX.length()).trim();
        String upperRemainder = remainder.toUpperCase();
        int whereIndex = upperRemainder.indexOf("WHERE");

        String queriedTable = (whereIndex >= 0 ? remainder.substring(0, whereIndex) : remainder).trim();
        if (queriedTable.isEmpty()) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }
        if (!queriedTable.equalsIgnoreCase(tableName)) {
            throw new IllegalArgumentException("Query references unknown table '" + queriedTable + "'.");
        }

        Path dataFile = tablePath.resolve("data.dat");
        if (whereIndex < 0) {
            return recordStorage.readRows(dataFile);
        }

        String whereClause = remainder.substring(whereIndex + "WHERE".length()).trim();
        Matcher matcher = WHERE_PATTERN.matcher(whereClause);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported WHERE clause: " + whereClause + " (expected <column> <operator> <value>, e.g. age > 20)");
        }
        String column = matcher.group(1);
        String operator = toInternalOperator(matcher.group(2));
        String rawValue = matcher.group(3).trim();
        return recordStorage.filterRows(dataFile, column, operator, parseValue(rawValue));
    }

    /** Maps the MySQL-style symbol the person typed to RecordStorage's internal operator codes. */
    private String toInternalOperator(String symbol) {
        switch (symbol) {
            case "=":  return "EQ";
            case "!=":
            case "<>": return "NEQ";
            case ">":  return "GT";
            case "<":  return "LT";
            case ">=": return "GTE";
            case "<=": return "LTE";
            default:   throw new IllegalArgumentException("Unsupported operator: " + symbol);
        }
    }

    private Object parseValue(String value) {
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        }
        if (trimmed.matches("-?\\d+")) {
            return Integer.parseInt(trimmed);
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return Double.parseDouble(trimmed);
        }
        return trimmed.replace("'", "").replace("\"", "");
    }
}
