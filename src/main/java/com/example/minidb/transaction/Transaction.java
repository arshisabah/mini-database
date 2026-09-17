package com.example.minidb.transaction;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A single in-flight transaction scoped to one table.
 *
 * Nothing here is written to the live data file. All row operations made
 * under this transaction's id are redirected to {@link #getWorkingFile()},
 * a private on-disk copy that started as a clone of the table's data at the
 * moment the transaction began. Commit atomically swaps that working copy
 * in for the live file; rollback simply deletes it. Either way the live
 * file is untouched until commit.
 */
public class Transaction {

    private final String id;
    private final String databaseName;
    private final String tableName;
    private final Path workingFile;
    private final Instant createdAt;

    public Transaction(String id, String databaseName, String tableName, Path workingFile) {
        this.id = id;
        this.databaseName = databaseName;
        this.tableName = tableName;
        this.workingFile = workingFile;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getTableName() {
        return tableName;
    }

    public Path getWorkingFile() {
        return workingFile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
