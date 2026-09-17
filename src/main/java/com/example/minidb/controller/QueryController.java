package com.example.minidb.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.example.minidb.dto.QueryRequest;
import com.example.minidb.engine.DatabaseEngine;
import com.example.minidb.engine.TableEngine;
import com.example.minidb.service.RowService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the small "SELECT * FROM table [WHERE ...]" query engine over REST.
 * This was previously missing: QueryEngine, QueryRequest and RowService.query()
 * existed but had no controller wiring them to an endpoint, so the feature
 * described in the project guide (POST /api/query) was unreachable.
 *
 * Since MiniDB scopes tables under a database, the endpoint is nested under
 * the database, matching the rest of the API:
 *
 *   POST /api/databases/{databaseName}/query
 *   { "query": "SELECT * FROM students WHERE age GT 20" }
 */
@RestController
@RequestMapping("/api/databases")
public class QueryController {

    private final DatabaseEngine databaseEngine;
    private final TableEngine tableEngine;
    private final RowService rowService;

    public QueryController(DatabaseEngine databaseEngine, TableEngine tableEngine, RowService rowService) {
        this.databaseEngine = databaseEngine;
        this.tableEngine = tableEngine;
        this.rowService = rowService;
    }

    @PostMapping("/{databaseName}/query")
    public List<Map<String, Object>> runQuery(@PathVariable String databaseName,
                                               @RequestBody QueryRequest request) throws IOException {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Query is required.");
        }

        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        String tableName = extractTableName(request.getQuery());

        // Validates the table exists and has valid metadata (throws TableNotFoundException otherwise).
        tableEngine.getTable(databasePath, tableName);
        Path tablePath = databasePath.resolve(tableName);

        return rowService.query(tablePath, tableName, request.getQuery());
    }

    private String extractTableName(String query) {
        String normalized = query.trim().replaceAll("\\s+", " ");
        String upper = normalized.toUpperCase();
        int fromIndex = upper.indexOf("FROM");
        if (fromIndex < 0) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }
        String remainder = normalized.substring(fromIndex + "FROM".length()).trim();
        int whereIndex = remainder.toUpperCase().indexOf("WHERE");
        String tableName = (whereIndex >= 0 ? remainder.substring(0, whereIndex) : remainder).trim();
        if (tableName.isEmpty()) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }
        return tableName;
    }
}
