package com.example.minidb.transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.example.minidb.exception.TransactionNotFoundException;
import com.example.minidb.storage.FileStorage;
import org.springframework.stereotype.Component;

/**
 * Gives MiniDB real, server-side commit/rollback instead of the "fire every
 * request and hope" behavior the REST API had before: begin() clones the
 * table's current data.dat into a private working file, every row endpoint
 * called with that transaction's id reads and writes only that copy, and
 * commit()/rollback() are the only two operations that ever touch the live
 * file — via a single atomic move, or not at all.
 *
 * This keeps things simple on purpose, matching the rest of MiniDB:
 * - One in-memory map, scoped to this JVM instance (no cross-restart durability).
 * - A transaction is scoped to exactly one table.
 * - Concurrent transactions on the *same* table are each given their own
 *   working file, so they won't corrupt each other, but the usual multi-writer
 *   caveat applies: whichever commits second overwrites the first with its
 *   own snapshot instead of merging. Good enough for a single-user console;
 *   not a substitute for real MVCC/locking.
 */
@Component
public class TransactionManager {

    private static final String LIVE_FILE_NAME = "data.dat";
    private static final String STAGING_DIR_NAME = ".transactions";

    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final FileStorage fileStorage = new FileStorage();

    /**
     * Starts a transaction against {@code tableName} and returns it. The
     * returned transaction's id is what callers pass back on every row
     * request, and to commit()/rollback().
     */
    public Transaction begin(String databaseName, String tableName, Path tablePath) throws IOException {
        String id = UUID.randomUUID().toString();
        Path stagingDir = tablePath.resolve(STAGING_DIR_NAME);
        fileStorage.createDirectories(stagingDir);

        Path liveFile = tablePath.resolve(LIVE_FILE_NAME);
        Path workingFile = stagingDir.resolve(id + ".dat");
        if (Files.exists(liveFile)) {
            Files.copy(liveFile, workingFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            fileStorage.createFile(workingFile);
        }

        Transaction transaction = new Transaction(id, databaseName, tableName, workingFile);
        transactions.put(id, transaction);
        return transaction;
    }

    /** Looks up a transaction and verifies it belongs to the given database/table. */
    public Transaction get(String databaseName, String tableName, String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        if (transaction == null
                || !transaction.getDatabaseName().equals(databaseName)
                || !transaction.getTableName().equals(tableName)) {
            throw new TransactionNotFoundException(
                    "No active transaction '" + transactionId + "' for " + databaseName + "." + tableName);
        }
        return transaction;
    }

    /**
     * Resolves which data file a row operation should read/write:
     * the transaction's private working file when a transactionId is given,
     * otherwise the table's live data file. This is the single seam that
     * lets row endpoints stay oblivious to whether they're inside a
     * transaction or not.
     */
    public Path resolveDataFile(String databaseName, String tableName, Path tablePath, String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return tablePath.resolve(LIVE_FILE_NAME);
        }
        return get(databaseName, tableName, transactionId).getWorkingFile();
    }

    /** Atomically replaces the live file with the transaction's working copy. */
    public void commit(String databaseName, String tableName, Path tablePath, String transactionId) throws IOException {
        Transaction transaction = get(databaseName, tableName, transactionId);
        Path liveFile = tablePath.resolve(LIVE_FILE_NAME);
        Files.move(transaction.getWorkingFile(), liveFile, StandardCopyOption.REPLACE_EXISTING);
        transactions.remove(transactionId);
    }

    /** Discards the transaction's working copy. The live file is never touched. */
    public void rollback(String databaseName, String tableName, String transactionId) throws IOException {
        Transaction transaction = get(databaseName, tableName, transactionId);
        Files.deleteIfExists(transaction.getWorkingFile());
        transactions.remove(transactionId);
    }
}
