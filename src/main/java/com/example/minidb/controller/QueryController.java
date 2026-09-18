package com.example.minidb.controller;

import java.io.IOException;

import com.example.minidb.dto.QueryRequest;
import com.example.minidb.engine.SqlEngine;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the query console over REST for anything scoped to one database:
 * SELECT, INSERT, UPDATE, DELETE, CREATE TABLE, DROP TABLE. Everything here
 * is parsed and executed by SqlEngine, which runs it immediately against
 * the live data (no staging/commit step -- see TransactionController if you
 * want that).
 *
 *   POST /api/databases/{databaseName}/query
 *   { "query": "SELECT * FROM students WHERE age > 20" }
 *   { "query": "INSERT INTO students (id, name, age, department) VALUES (5, 'Meera', 22, 'ECE')" }
 *   { "query": "UPDATE students SET age = 23 WHERE id = 5" }
 *   { "query": "DELETE FROM students WHERE id = 5" }
 *   { "query": "CREATE TABLE staff (id INT PRIMARY KEY, name VARCHAR)" }
 *   { "query": "DROP TABLE staff" }
 *
 * CREATE DATABASE / DROP DATABASE aren't scoped to an existing database, so
 * they're not accepted here -- see GlobalQueryController's POST /api/query.
 *
 * Response shape: a SELECT returns a JSON array of rows, same as before.
 * Every other statement returns a message object, e.g.
 * {"message": "...", "rowsAffected": 2}. The frontend distinguishes the two
 * by checking Array.isArray(...) on the response.
 */
@RestController
@RequestMapping("/api/databases")
public class QueryController {

    private final SqlEngine sqlEngine;

    public QueryController(SqlEngine sqlEngine) {
        this.sqlEngine = sqlEngine;
    }

    @PostMapping("/{databaseName}/query")
    public Object runQuery(@PathVariable String databaseName,
                            @RequestBody QueryRequest request) throws IOException {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("Query is required.");
        }
        return sqlEngine.execute(databaseName, request.getQuery());
    }
}
