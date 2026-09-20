package com.example.minidb.transaction;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single in-flight transaction scoped to one database -- not one table.
 * A real MySQL transaction can touch as many tables as you like before you
 * COMMIT or ROLLBACK; this mirrors that instead of the earlier, narrower
 * one-table-per-transaction design.
 *
 * Nothing here is written to a live data file. Each table this transaction
 * touches gets its own private working copy (see {@link #getWorkingFile}),
 * cloned from that table's live data the first time the transaction reads
 * or writes it -- not all at once when the transaction begins, since most
 * transactions only touch a handful of a database's tables. Commit
 * atomically swaps each touched table's working copy in for its live file;
 * rollback just deletes all of them. Either way, no live file is touched
 * until commit.
 */
public class Transaction {

    private final String id;
    private final String databaseName;
    private final Instant createdAt;
    private final Map<String, Path> workingFiles = new ConcurrentHashMap<>();

    public Transaction(String id, String databaseName) {
        this.id = id;
        this.databaseName = databaseName;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** The table's private working copy within this transaction, or null if this transaction hasn't touched it yet. */
    public Path getWorkingFile(String tableName) {
        return workingFiles.get(tableName);
    }

    public void putWorkingFile(String tableName, Path workingFile) {
        workingFiles.put(tableName, workingFile);
    }

    /** Every table this transaction has touched so far, mapped to its working copy. Live view -- do not mutate from outside TransactionManager. */
    public Map<String, Path> workingFiles() {
        return workingFiles;
    }
}
