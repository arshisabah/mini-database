package com.example.minidb.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.example.minidb.engine.DatabaseEngine;
import com.example.minidb.engine.TableEngine;
import com.example.minidb.model.Table;
import com.example.minidb.service.RowService;
import com.example.minidb.transaction.TransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/databases")
public class RowController {

    private final RowService rowService;
    private final DatabaseEngine databaseEngine;
    private final TableEngine tableEngine;
    private final TransactionManager transactionManager;

    public RowController(RowService rowService, DatabaseEngine databaseEngine, TableEngine tableEngine,
                          TransactionManager transactionManager) {
        this.rowService = rowService;
        this.databaseEngine = databaseEngine;
        this.tableEngine = tableEngine;
        this.transactionManager = transactionManager;
    }

    /**
     * Every row endpoint below takes an optional {@code transactionId} query
     * param. Omit it and the request hits the table's live data straight
     * away, exactly as before. Pass the id returned by
     * {@code POST .../transactions} and the request is redirected to that
     * transaction's private staging file instead -- nothing lands in the
     * live file until that transaction is committed (see
     * TransactionController).
     */

    @PostMapping("/{databaseName}/tables/{tableName}/rows")
    public ResponseEntity<Map<String, Object>> insertRow(@PathVariable String databaseName,
                                                  @PathVariable String tableName,
                                                  @RequestParam(required = false) String transactionId,
                                                  @RequestBody Map<String, Object> row) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path tablePath = databasePath.resolve(tableName);
        Path dataFile = transactionManager.resolveDataFile(databaseName, tableName, tablePath, transactionId);
        rowService.insertRow(dataFile, row, table);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Row inserted successfully"));
    }

    @GetMapping("/{databaseName}/tables/{tableName}/rows")
    public List<Map<String, Object>> getRows(@PathVariable String databaseName,
                                            @PathVariable String tableName,
                                            @RequestParam(required = false) String column,
                                            @RequestParam(required = false) String operator,
                                            @RequestParam(required = false) String value,
                                            @RequestParam(required = false) String transactionId) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path tablePath = databasePath.resolve(tableName);
        Path dataFile = transactionManager.resolveDataFile(databaseName, tableName, tablePath, transactionId);
        if (column != null && operator != null && value != null) {
            return rowService.filterRows(dataFile, column, operator, parseValue(value), table);
        }
        return rowService.getRows(dataFile);
    }

    @GetMapping("/{databaseName}/tables/{tableName}/rows/{id}")
    public Map<String, Object> getRowById(@PathVariable String databaseName,
                                         @PathVariable String tableName,
                                         @PathVariable String id,
                                         @RequestParam(required = false) String transactionId) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path tablePath = databasePath.resolve(tableName);
        Path dataFile = transactionManager.resolveDataFile(databaseName, tableName, tablePath, transactionId);
        Object idValue = rowService.parseIdValue(table, id);
        return rowService.getRowById(dataFile, table, idValue);
    }

    @PutMapping("/{databaseName}/tables/{tableName}/rows/{id}")
    public Map<String, Object> updateRow(@PathVariable String databaseName,
                                        @PathVariable String tableName,
                                        @PathVariable String id,
                                        @RequestParam(required = false) String transactionId,
                                        @RequestBody Map<String, Object> row) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path tablePath = databasePath.resolve(tableName);
        Path dataFile = transactionManager.resolveDataFile(databaseName, tableName, tablePath, transactionId);
        Object idValue = rowService.parseIdValue(table, id);
        rowService.updateRow(dataFile, idValue, row, table);
        return Map.of("message", "Row updated successfully");
    }

    @DeleteMapping("/{databaseName}/tables/{tableName}/rows/{id}")
    public Map<String, Object> deleteRow(@PathVariable String databaseName,
                                        @PathVariable String tableName,
                                        @PathVariable String id,
                                        @RequestParam(required = false) String transactionId) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path tablePath = databasePath.resolve(tableName);
        Path dataFile = transactionManager.resolveDataFile(databaseName, tableName, tablePath, transactionId);
        Object idValue = rowService.parseIdValue(table, id);
        rowService.deleteRow(dataFile, table, idValue);
        return Map.of("message", "Row deleted successfully");
    }

    private Object parseValue(String value) {
        if (value == null) {
            return null;
        }
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
