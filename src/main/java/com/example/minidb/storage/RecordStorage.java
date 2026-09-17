package com.example.minidb.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.minidb.exception.DuplicateKeyException;
import com.example.minidb.exception.RecordNotFoundException;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Reads and writes rows to a specific data file.
 *
 * Every method takes the exact {@code dataFile} to operate on rather than
 * assuming "the table's live data.dat" -- callers (RowService, via
 * TransactionManager) decide whether that's the live file or a
 * transaction's private working copy. RecordStorage itself has no idea
 * transactions exist; it just reads/writes whichever file it's handed.
 */
@Component
public class RecordStorage {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FileStorage fileStorage = new FileStorage();

    public void insertRow(Path dataFile, Map<String, Object> row, Table table) throws IOException {
        List<Map<String, Object>> existingRows = readRows(dataFile);
        Column pkColumn = findPrimaryKey(table);
        Object pkValue = row.get(pkColumn.getName());
        for (Map<String, Object> existingRow : existingRows) {
            if (Objects.equals(existingRow.get(pkColumn.getName()), pkValue)) {
                throw new DuplicateKeyException("Duplicate primary key value: " + pkValue);
            }
        }
        existingRows.add(row);
        writeRows(dataFile, existingRows);
    }

    public List<Map<String, Object>> readRows(Path dataFile) throws IOException {
        if (!Files.exists(dataFile)) {
            return new ArrayList<>();
        }
        List<String> lines = fileStorage.readLines(dataFile);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            Map<String, Object> row = objectMapper.readValue(line, Map.class);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> readRowById(Path dataFile, Table table, Object id) throws IOException {
        Column pkColumn = findPrimaryKey(table);
        List<Map<String, Object>> rows = readRows(dataFile);
        for (Map<String, Object> row : rows) {
            if (Objects.equals(row.get(pkColumn.getName()), id)) {
                return row;
            }
        }
        throw new RecordNotFoundException("Record with " + pkColumn.getName() + " '" + id + "' not found");
    }

    public void updateRow(Path dataFile, Table table, Object id, Map<String, Object> updatedRow) throws IOException {
        Column pkColumn = findPrimaryKey(table);
        List<Map<String, Object>> rows = readRows(dataFile);
        boolean found = false;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (Objects.equals(row.get(pkColumn.getName()), id)) {
                Map<String, Object> merged = new HashMap<>(row);
                merged.putAll(updatedRow);
                rows.set(i, merged);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RecordNotFoundException("Record with " + pkColumn.getName() + " '" + id + "' not found");
        }
        writeRows(dataFile, rows);
    }

    public void deleteRow(Path dataFile, Table table, Object id) throws IOException {
        Column pkColumn = findPrimaryKey(table);
        List<Map<String, Object>> rows = readRows(dataFile);
        List<Map<String, Object>> updated = new ArrayList<>();
        boolean found = false;
        for (Map<String, Object> row : rows) {
            if (Objects.equals(row.get(pkColumn.getName()), id)) {
                found = true;
            } else {
                updated.add(row);
            }
        }
        if (!found) {
            throw new RecordNotFoundException("Record with " + pkColumn.getName() + " '" + id + "' not found");
        }
        writeRows(dataFile, updated);
    }

    public void writeRows(Path dataFile, List<Map<String, Object>> rows) throws IOException {
        Path tempFile = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
        StringBuilder content = new StringBuilder();
        for (Map<String, Object> row : rows) {
            content.append(objectMapper.writeValueAsString(row)).append(System.lineSeparator());
        }
        fileStorage.writeText(tempFile, content.toString());
        Files.move(tempFile, dataFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public List<Map<String, Object>> filterRows(Path dataFile, String column, String operator, Object value) throws IOException {
        List<Map<String, Object>> rows = readRows(dataFile);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object actualValue = row.get(column);
            if (actualValue == null) {
                continue;
            }
            boolean match = false;
            switch (operator) {
                case "EQ":
                    match = Objects.equals(actualValue, value);
                    break;
                case "NEQ":
                    match = !Objects.equals(actualValue, value);
                    break;
                case "GT":
                    match = compare(actualValue, value) > 0;
                    break;
                case "LT":
                    match = compare(actualValue, value) < 0;
                    break;
                case "GTE":
                    match = compare(actualValue, value) >= 0;
                    break;
                case "LTE":
                    match = compare(actualValue, value) <= 0;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
            if (match) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            double l = ((Number) left).doubleValue();
            double r = ((Number) right).doubleValue();
            return Double.compare(l, r);
        }
        if (left instanceof String && right instanceof String) {
            return ((String) left).compareTo((String) right);
        }
        throw new IllegalArgumentException("Unsupported comparison type for filter.");
    }

    private Column findPrimaryKey(Table table) {
        for (Column column : table.getColumns()) {
            if (column.isPrimaryKey()) {
                return column;
            }
        }
        throw new IllegalArgumentException("Table does not define a primary key.");
    }
}
