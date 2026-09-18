package com.example.minidb.controller;

import java.io.IOException;

import com.example.minidb.dto.QueryRequest;
import com.example.minidb.engine.SqlEngine;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one query endpoint that doesn't require an existing database, because
 * its two supported statements target the database level itself:
 *
 *   POST /api/query
 *   { "query": "CREATE DATABASE college" }
 *   { "query": "DROP DATABASE college" }
 *
 * Everything else (SELECT, INSERT, UPDATE, DELETE, CREATE/DROP TABLE) runs
 * against QueryController's POST /api/databases/{databaseName}/query
 * instead, since those are meaningless without a database to run them in.
 */
@RestController
@RequestMapping("/api")
public class GlobalQueryController {

    private final SqlEngine sqlEngine;

    public GlobalQueryController(SqlEngine sqlEngine) {
        this.sqlEngine = sqlEngine;
    }

    @PostMapping("/query")
    public Object runGlobalQuery(@RequestBody QueryRequest request) throws IOException {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Query is required.");
        }
        return sqlEngine.executeGlobal(request.getQuery());
    }
}
