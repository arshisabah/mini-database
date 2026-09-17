package com.example.minidb.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.example.minidb.engine.DatabaseEngine;
import com.example.minidb.engine.TableEngine;
import com.example.minidb.service.RowService;
import com.example.minidb.transaction.Transaction;
import com.example.minidb.transaction.TransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real, server-side commit/rollback for a table's rows.
 *
 * Workflow:
 *   1. POST   .../transactions                 -> { "transactionId": "..." }
 *   2. Any number of row inserts/updates/deletes, each with
 *      ?transactionId=... appended -- these touch only that transaction's
 *      private staging copy, never the live data.
 *   3a. POST  .../transactions/{id}/commit      -> staged copy atomically
 *       replaces the live file. All-or-nothing: if this call fails, the
 *       live file is guaranteed untouched.
 *   3b. POST  .../transactions/{id}/rollback    -> staged copy is discarded.
 *       The live file was never touched in the first place.
 *
 * A transaction that's never committed or rolled back just sits as an
 * orphaned staging file; MiniDB doesn't auto-expire it (no background
 * reaper), so well-behaved clients always resolve it one way or the other.
 */
@RestController
@RequestMapping("/api/databases")
public class TransactionController {

    private final DatabaseEngine databaseEngine;
    private final TableEngine tableEngine;
    private final TransactionManager transactionManager;
    private final RowService rowService;

    public TransactionController(DatabaseEngine databaseEngine, TableEngine tableEngine,
                                  TransactionManager transactionManager, RowService rowService) {
        this.databaseEngine = databaseEngine;
        this.tableEngine = tableEngine;
        this.transactionManager = transactionManager;
        this.rowService = rowService;
    }

    @PostMapping("/{databaseName}/tables/{tableName}/transactions")
    public ResponseEntity<Map<String, Object>> begin(@PathVariable String databaseName,
                                                       @PathVariable String tableName) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        tableEngine.getTable(databasePath, tableName); // validates the table exists
        Path tablePath = databasePath.resolve(tableName);

        Transaction transaction = transactionManager.begin(databaseName, tableName, tablePath);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "transactionId", transaction.getId(),
                "databaseName", databaseName,
                "tableName", tableName,
                "message", "Transaction started. Row changes are only visible once you commit."
        ));
    }

    @GetMapping("/{databaseName}/tables/{tableName}/transactions/{transactionId}")
    public Map<String, Object> status(@PathVariable String databaseName,
                                       @PathVariable String tableName,
                                       @PathVariable String transactionId) throws IOException {
        Transaction transaction = transactionManager.get(databaseName, tableName, transactionId);
        List<Map<String, Object>> stagedRows = rowService.getRows(transaction.getWorkingFile());
        return Map.of(
                "transactionId", transaction.getId(),
                "databaseName", databaseName,
                "tableName", tableName,
                "createdAt", transaction.getCreatedAt().toString(),
                "stagedRowCount", stagedRows.size()
        );
    }

    @PostMapping("/{databaseName}/tables/{tableName}/transactions/{transactionId}/commit")
    public Map<String, Object> commit(@PathVariable String databaseName,
                                       @PathVariable String tableName,
                                       @PathVariable String transactionId) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Path tablePath = databasePath.resolve(tableName);
        transactionManager.commit(databaseName, tableName, tablePath, transactionId);
        return Map.of("message", "Transaction committed.", "transactionId", transactionId);
    }

    @PostMapping("/{databaseName}/tables/{tableName}/transactions/{transactionId}/rollback")
    public Map<String, Object> rollback(@PathVariable String databaseName,
                                         @PathVariable String tableName,
                                         @PathVariable String transactionId) throws IOException {
        transactionManager.rollback(databaseName, tableName, transactionId);
        return Map.of("message", "Transaction rolled back. The live data was never touched.", "transactionId", transactionId);
    }
}
