package com.example.minidb.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.example.minidb.storage.RecordStorage;
import org.springframework.stereotype.Component;

@Component
public class QueryEngine {

    private static final String SELECT_PREFIX = "SELECT * FROM";

    private final RecordStorage recordStorage;

    public QueryEngine(RecordStorage recordStorage) {
        this.recordStorage = recordStorage;
    }

    public List<Map<String, Object>> selectAll(Path tablePath) throws IOException {
        return recordStorage.readRows(tablePath);
    }

    /**
     * Parses a very small "SELECT * FROM <table> [WHERE <col> <op> <value>]"
     * query. The table name is not hardcoded: whatever table name appears in
     * the query text is matched (case-insensitively) against the table the
     * caller resolved from the URL, so this works for any table, not just
     * "students".
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

        if (whereIndex < 0) {
            return recordStorage.readRows(tablePath);
        }

        String whereClause = remainder.substring(whereIndex + "WHERE".length()).trim();
        String[] parts = whereClause.split(" ");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }
        String column = parts[0];
        String operator = parts[1].toUpperCase();
        String value = parts[2];
        return recordStorage.filterRows(tablePath, column, operator, parseValue(value));
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
