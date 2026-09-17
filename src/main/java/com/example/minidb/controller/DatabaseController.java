package com.example.minidb.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.minidb.dto.CreateDatabaseRequest;
import com.example.minidb.service.DatabaseService;
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
@RequestMapping("/api")
public class DatabaseController {

    private final DatabaseService databaseService;

    public DatabaseController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @PostMapping("/databases")
    public ResponseEntity<Map<String, Object>> createDatabase(@RequestBody CreateDatabaseRequest request) throws IOException {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Database name is required.");
        }
        databaseService.createDatabase(request.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Database created successfully");
        response.put("database", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/databases")
    public List<String> listDatabases() throws IOException {
        return databaseService.listDatabases();
    }

    @GetMapping("/databases/{databaseName}")
    public Map<String, Object> getDatabase(@PathVariable String databaseName) {
        return Map.of("name", databaseName, "path", databaseService.getDatabasePath(databaseName).toString());
    }

    @DeleteMapping("/databases/{databaseName}")
    public Map<String, Object> deleteDatabase(@PathVariable String databaseName) throws IOException {
        databaseService.deleteDatabase(databaseName);
        return Map.of("message", "Database deleted successfully", "database", databaseName);
    }
}
