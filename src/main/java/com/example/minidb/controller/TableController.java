package com.example.minidb.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.minidb.dto.AlterTableRequest;
import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.engine.DatabaseEngine;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.example.minidb.service.TableService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/databases")
public class TableController {

    private final TableService tableService;
    private final DatabaseEngine databaseEngine;

    public TableController(TableService tableService, DatabaseEngine databaseEngine) {
        this.tableService = tableService;
        this.databaseEngine = databaseEngine;
    }

    @PostMapping("/{databaseName}/tables")
    public ResponseEntity<Map<String, Object>> createTable(@PathVariable String databaseName,
                                             @RequestBody CreateTableRequest request) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        tableService.createTable(databasePath, request);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Table created successfully");
        response.put("database", databaseName);
        response.put("table", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{databaseName}/tables")
    public List<String> listTables(@PathVariable String databaseName) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        return tableService.listTables(databasePath);
    }

    @GetMapping("/{databaseName}/tables/{tableName}")
    public Table getTable(@PathVariable String databaseName, @PathVariable String tableName) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        return tableService.getTable(databasePath, tableName);
    }

    @DeleteMapping("/{databaseName}/tables/{tableName}")
    public Map<String, Object> deleteTable(@PathVariable String databaseName, @PathVariable String tableName) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        tableService.deleteTable(databasePath, tableName);
        return Map.of("message", "Table deleted successfully", "database", databaseName, "table", tableName);
    }

    /**
     * ALTER TABLE, as a REST call. {@code request.operation} picks which
     * fields of the body matter -- see AlterTableRequest's javadoc. The
     * same underlying TableService methods back the query-console
     * {@code ALTER TABLE ...} statements (see SqlEngine), so both surfaces
     * apply exactly the same validation and row migration.
     */
    @PatchMapping("/{databaseName}/tables/{tableName}")
    public Table alterTable(@PathVariable String databaseName, @PathVariable String tableName,
                             @RequestBody AlterTableRequest request) throws IOException {
        if (request == null || request.getOperation() == null) {
            throw new IllegalArgumentException("operation is required (ADD_COLUMN, DROP_COLUMN, RENAME_COLUMN, or MODIFY_COLUMN).");
        }
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        switch (request.getOperation()) {
            case ADD_COLUMN:
                if (request.getColumn() == null || request.getColumn().getName() == null || request.getColumn().getDataType() == null) {
                    throw new IllegalArgumentException("ADD_COLUMN requires 'column' with a name and dataType.");
                }
                return tableService.addColumn(databasePath, tableName,
                        new Column(request.getColumn().getName(), request.getColumn().getDataType(), false));
            case DROP_COLUMN:
                if (request.getColumnName() == null) {
                    throw new IllegalArgumentException("DROP_COLUMN requires 'columnName'.");
                }
                return tableService.dropColumn(databasePath, tableName, request.getColumnName());
            case RENAME_COLUMN:
                if (request.getColumnName() == null || request.getNewColumnName() == null) {
                    throw new IllegalArgumentException("RENAME_COLUMN requires 'columnName' and 'newColumnName'.");
                }
                return tableService.renameColumn(databasePath, tableName, request.getColumnName(), request.getNewColumnName());
            case MODIFY_COLUMN:
                if (request.getColumnName() == null || request.getDataType() == null) {
                    throw new IllegalArgumentException("MODIFY_COLUMN requires 'columnName' and 'dataType'.");
                }
                return tableService.modifyColumnType(databasePath, tableName, request.getColumnName(), request.getDataType());
            default:
                throw new IllegalArgumentException("Unsupported operation: " + request.getOperation());
        }
    }
}
