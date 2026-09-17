package com.example.minidb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.minidb.engine.QueryEngine;
import com.example.minidb.enums.DataType;
import com.example.minidb.exception.DuplicateKeyException;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.example.minidb.storage.RecordStorage;
import com.example.minidb.validator.DataValidator;
import org.springframework.stereotype.Service;

@Service
public class RowService {

    private final RecordStorage recordStorage;
    private final QueryEngine queryEngine;
    private final DataValidator dataValidator = new DataValidator();

    public RowService(RecordStorage recordStorage, QueryEngine queryEngine) {
        this.recordStorage = recordStorage;
        this.queryEngine = queryEngine;
    }

    /**
     * Every method below takes {@code dataFile} -- the exact file to read or
     * write. The caller (RowController) resolves that path via
     * TransactionManager: the table's live data.dat when no transaction id
     * was supplied, or a transaction's private staging file when one was.
     * RowService stays unaware of transactions; it just acts on whichever
     * file it's handed, which is what makes staged changes invisible to
     * everyone else until commit.
     */
    public void insertRow(Path dataFile, Map<String, Object> row, Table table) throws IOException {
        dataValidator.validateRecord(row, table);
        checkPrimaryKeyDuplication(dataFile, row, table);
        recordStorage.insertRow(dataFile, row, table);
    }

    public List<Map<String, Object>> getRows(Path dataFile) throws IOException {
        return recordStorage.readRows(dataFile);
    }

    public Map<String, Object> getRowById(Path dataFile, Table table, Object id) throws IOException {
        return recordStorage.readRowById(dataFile, table, id);
    }

    public void updateRow(Path dataFile, Object id, Map<String, Object> row, Table table) throws IOException {
        dataValidator.validatePartialRecord(row, table);
        Column pk = findPrimaryKey(table);
        if (row.containsKey(pk.getName()) && !id.equals(row.get(pk.getName()))) {
            throw new IllegalArgumentException("Primary key '" + pk.getName() + "' in payload does not match route ID.");
        }
        Map<String, Object> currentRow = recordStorage.readRowById(dataFile, table, id);
        Map<String, Object> merged = new HashMap<>(currentRow);
        merged.putAll(row);
        recordStorage.updateRow(dataFile, table, id, merged);
    }

    public void deleteRow(Path dataFile, Table table, Object id) throws IOException {
        recordStorage.deleteRow(dataFile, table, id);
    }

    /**
     * Converts the raw path-variable id (always a String from the URL) into the
     * correct Java type based on the table's declared primary key data type, so
     * lookups match what's actually stored (e.g. Integer for INT, Double for
     * DOUBLE, plain String for VARCHAR/DATE).
     */
    public Object parseIdValue(Table table, String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new IllegalArgumentException("Id value is required.");
        }
        Column pk = findPrimaryKey(table);
        DataType type = pk.getDataType();
        try {
            if (type == DataType.INT) {
                return Integer.parseInt(rawId);
            }
            if (type == DataType.DOUBLE) {
                return Double.parseDouble(rawId);
            }
            if (type == DataType.BOOLEAN) {
                return Boolean.parseBoolean(rawId);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid id value '" + rawId + "' for column '" + pk.getName() + "'. Expected " + type + ".");
        }
        return rawId;
    }

    public List<Map<String, Object>> filterRows(Path dataFile, String column, String operator, Object value, Table table) throws IOException {
        if (table == null) {
            throw new IllegalArgumentException("Table metadata is required for filtering.");
        }
        boolean knownColumn = table.getColumns().stream().anyMatch(c -> c.getName().equals(column));
        if (!knownColumn) {
            throw new IllegalArgumentException("Unknown column: '" + column + "'.");
        }
        return recordStorage.filterRows(dataFile, column, operator, value);
    }

    public List<Map<String, Object>> query(Path tablePath, String tableName, String query) throws IOException {
        return queryEngine.parseSelectQuery(tablePath, tableName, query);
    }

    private void checkPrimaryKeyDuplication(Path dataFile, Map<String, Object> row, Table table) throws IOException {
        Column pk = findPrimaryKey(table);

        Object pkValue = row.get(pk.getName());
        for (Map<String, Object> existingRow : recordStorage.readRows(dataFile)) {
            if (existingRow.containsKey(pk.getName()) && existingRow.get(pk.getName()).equals(pkValue)) {
                throw new DuplicateKeyException("Duplicate primary key value: " + pkValue);
            }
        }
    }

    private Column findPrimaryKey(Table table) {
        return table.getColumns().stream()
                .filter(Column::isPrimaryKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table does not define a primary key."));
    }
}
