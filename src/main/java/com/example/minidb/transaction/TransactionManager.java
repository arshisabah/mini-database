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
 * request and hope" behavior the REST API had originally.
 *
 * Two ways to use it, sharing the same underlying mechanism:
 *
 * 1. Explicit, per-transaction-id (what the row-editing UI and
 *    TransactionController's REST endpoints use): begin() hands back an id;
 *    every row request tagged with that id reads/writes that transaction's
 *    private working copy instead of the live file, until commit() or
 *    rollback() resolves it. The caller carries the id around itself.
 *
 * 2. Implicit, one-per-database "session" transaction (what SQL-text
 *    BEGIN/COMMIT/ROLLBACK use, via SqlEngine): beginActive() starts a
 *    transaction and remembers it as *the* current transaction for that
 *    database, so statements against that database automatically join it
 *    -- via {@link #activeTransactionId} -- without carrying an id on every
 *    request, the way a real MySQL client session doesn't require one
 *    either. commitActive()/rollbackActive() resolve whichever transaction
 *    is currently active, and are harmless no-ops if none is (matching
 *    real MySQL: COMMIT/ROLLBACK with nothing open just does nothing).
 *
 * Kept intentionally simple:
 * - One in-memory map, scoped to this JVM instance (no cross-restart durability).
 * - Only one *active* (implicit) transaction per database at a time -- starting
 *   a second one implicitly commits the first, same as MySQL's autocommit-off
 *   session behavior. Concurrent *explicit* transactions (route 1) are
 *   unaffected by this limit; each gets its own id and working copies.
 * - Committing several tables in one transaction is NOT atomic *across*
 *   those tables -- each table's swap is its own atomic file move, done one
 *   after another. If the third of five swaps fails, the first two are
 *   already committed. Good enough for a single-user learning console; not
 *   a substitute for real multi-table ACID commit.
 */
@Component
public class TransactionManager {

    private static final String LIVE_FILE_NAME = "data.dat";
    private static final String STAGING_DIR_NAME = ".transactions";

    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, String> activeByDatabase = new ConcurrentHashMap<>();
    private final FileStorage fileStorage = new FileStorage();

    public Transaction begin(String databaseName) {
        String id = UUID.randomUUID().toString();
        Transaction transaction = new Transaction(id, databaseName);
        transactions.put(id, transaction);
        return transaction;
    }

    /** Looks up a transaction and verifies it belongs to the given database. */
    public Transaction get(String databaseName, String transactionId) {
        Transaction transaction = transactions.get(transactionId);
        if (transaction == null || !transaction.getDatabaseName().equals(databaseName)) {
            throw new TransactionNotFoundException("No active transaction '" + transactionId + "' for database '" + databaseName + "'.");
        }
        return transaction;
    }

    /**
     * Resolves which data file a row operation should read/write:
     * the transaction's private working copy for that table when a
     * transactionId is given (cloning the table's live data into it the
     * first time this transaction touches that table), otherwise the
     * table's live data file.
     */
    public Path resolveDataFile(String databaseName, String tableName, Path tablePath, String transactionId) throws IOException {
        if (transactionId == null || transactionId.isBlank()) {
            return tablePath.resolve(LIVE_FILE_NAME);
        }
        Transaction transaction = get(databaseName, transactionId);
        return workingFileFor(transaction, tableName, tablePath);
    }

    private Path workingFileFor(Transaction transaction, String tableName, Path tablePath) throws IOException {
        Path existing = transaction.getWorkingFile(tableName);
        if (existing != null) {
            return existing;
        }
        Path stagingDir = tablePath.resolve(STAGING_DIR_NAME);
        fileStorage.createDirectories(stagingDir);
        Path liveFile = tablePath.resolve(LIVE_FILE_NAME);
        Path workingFile = stagingDir.resolve(transaction.getId() + ".dat");
        if (Files.exists(liveFile)) {
            Files.copy(liveFile, workingFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            fileStorage.createFile(workingFile);
        }
        transaction.putWorkingFile(tableName, workingFile);
        return workingFile;
    }

    /** Atomically swaps every table this transaction touched -- one file move per table, in sequence (see class javadoc on cross-table atomicity). */
    public void commit(String databaseName, Path databasePath, String transactionId) throws IOException {
        Transaction transaction = get(databaseName, transactionId);
        for (Map.Entry<String, Path> entry : transaction.workingFiles().entrySet()) {
            Path liveFile = databasePath.resolve(entry.getKey()).resolve(LIVE_FILE_NAME);
            Files.move(entry.getValue(), liveFile, StandardCopyOption.REPLACE_EXISTING);
        }
        forget(databaseName, transactionId);
    }

    /** Discards every working copy this transaction created. No live file is ever touched by a rollback. */
    public void rollback(String databaseName, String transactionId) throws IOException {
        Transaction transaction = get(databaseName, transactionId);
        for (Path workingFile : transaction.workingFiles().values()) {
            Files.deleteIfExists(workingFile);
        }
        forget(databaseName, transactionId);
    }

    private void forget(String databaseName, String transactionId) {
        transactions.remove(transactionId);
        activeByDatabase.remove(databaseName, transactionId); // no-op if a different transaction is active
    }

    // -----------------------------------------------------------------
    // Implicit "session" transaction -- backs SQL-text BEGIN/COMMIT/ROLLBACK
    // -----------------------------------------------------------------

    public boolean hasActive(String databaseName) {
        return activeByDatabase.containsKey(databaseName);
    }

    public String activeTransactionId(String databaseName) {
        return activeByDatabase.get(databaseName);
    }

    /** Starts a transaction and marks it as the active one for this database. Caller decides what to do if one was already active (see SqlEngine: real MySQL implicitly commits it). */
    public Transaction beginActive(String databaseName) {
        Transaction transaction = begin(databaseName);
        activeByDatabase.put(databaseName, transaction.getId());
        return transaction;
    }

    /** Commits whatever transaction is currently active for this database. A harmless no-op if none is -- same as MySQL's COMMIT with nothing open. */
    public void commitActive(String databaseName, Path databasePath) throws IOException {
        String transactionId = activeByDatabase.get(databaseName);
        if (transactionId == null) {
            return;
        }
        commit(databaseName, databasePath, transactionId);
    }

    /** Rolls back whatever transaction is currently active for this database. A harmless no-op if none is -- same as MySQL's ROLLBACK with nothing open. */
    public void rollbackActive(String databaseName) throws IOException {
        String transactionId = activeByDatabase.get(databaseName);
        if (transactionId == null) {
            return;
        }
        rollback(databaseName, transactionId);
    }
}
