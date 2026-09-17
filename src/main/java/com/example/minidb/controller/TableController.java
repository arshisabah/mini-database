package com.example.minidb.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.engine.DatabaseEngine;
import com.example.minidb.model.Table;
import com.example.minidb.service.TableService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
}
