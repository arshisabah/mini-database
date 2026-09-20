package com.example.minidb.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.minidb.dto.ColumnRequest;
import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.enums.DataType;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.example.minidb.service.DatabaseService;
import com.example.minidb.service.RowService;
import com.example.minidb.service.TableService;
import com.example.minidb.storage.RecordStorage;
import com.example.minidb.transaction.Transaction;
import com.example.minidb.transaction.TransactionManager;
import com.example.minidb.validator.DataValidator;
import org.springframework.stereotype.Component;

/**
 * Dispatches every supported SQL-text statement to the same services the
 * structured REST endpoints use. This is what lets the query console do
 * everything the UI forms can: create/drop a database, create/drop a
 * table, and insert/update/delete rows, on top of the SELECT support
 * QueryEngine already had.
 *
 * Every INSERT/UPDATE/DELETE/SELECT here runs immediately against the live
 * data file by default -- same as a real SQL client in autocommit mode --
 * UNLESS a BEGIN/START TRANSACTION has been run against this database and
 * not yet resolved, in which case they transparently join that transaction
 * instead (see TransactionManager's "active session transaction" concept).
 * That's on top of the row-editing UI's own separate staged-commit flow
 * (TransactionController), which still works exactly as before and doesn't
 * interact with SQL-text transactions at all.
 *
 * Deliberately out of scope, same as QueryEngine's SELECT parser: no
 * AND/OR, no joins, no multi-statement scripts, no SAVEPOINT, no isolation
 * level control (effectively always "read committed" outside a
 * transaction, "read your own writes" inside one). One statement, one
 * clause. ALTER TABLE covers ADD/DROP/RENAME/MODIFY COLUMN but not a
 * MODIFY that converts existing row values to the new type -- see
 * TableEngine's modifyColumnType javadoc.
 */
@Component
public class SqlEngine {

    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile("^CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.+)\\)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_TABLE_PATTERN =
            Pattern.compile("^DROP\\s+TABLE\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_ADD_PATTERN =
            Pattern.compile("^ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+(?:COLUMN\\s+)?(\\w+)\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_DROP_PATTERN =
            Pattern.compile("^ALTER\\s+TABLE\\s+(\\w+)\\s+DROP\\s+(?:COLUMN\\s+)?(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_RENAME_PATTERN =
            Pattern.compile("^ALTER\\s+TABLE\\s+(\\w+)\\s+RENAME\\s+(?:COLUMN\\s+)?(\\w+)\\s+TO\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_MODIFY_PATTERN =
            Pattern.compile("^ALTER\\s+TABLE\\s+(\\w+)\\s+MODIFY\\s+(?:COLUMN\\s+)?(\\w+)\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_PATTERN =
            Pattern.compile("^INSERT\\s+INTO\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*VALUES\\s*\\(([^)]*)\\)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UPDATE_PATTERN =
            Pattern.compile("^UPDATE\\s+(\\w+)\\s+SET\\s+(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DELETE_PATTERN =
            Pattern.compile("^DELETE\\s+FROM\\s+(\\w+)\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CREATE_DATABASE_PATTERN =
            Pattern.compile("^CREATE\\s+DATABASE\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_DATABASE_PATTERN =
            Pattern.compile("^DROP\\s+DATABASE\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_WORD = Pattern.compile("^(\\w+)");

    private final DatabaseEngine databaseEngine;
    private final TableEngine tableEngine;
    private final TableService tableService;
    private final DatabaseService databaseService;
    private final RowService rowService;
    private final RecordStorage recordStorage;
    private final QueryEngine queryEngine;
    private final TransactionManager transactionManager;
    private final DataValidator dataValidator = new DataValidator();

    public SqlEngine(DatabaseEngine databaseEngine, TableEngine tableEngine, TableService tableService,
                      DatabaseService databaseService, RowService rowService, RecordStorage recordStorage,
                      QueryEngine queryEngine, TransactionManager transactionManager) {
        this.databaseEngine = databaseEngine;
        this.tableEngine = tableEngine;
        this.tableService = tableService;
        this.databaseService = databaseService;
        this.rowService = rowService;
        this.recordStorage = recordStorage;
        this.queryEngine = queryEngine;
        this.transactionManager = transactionManager;
    }

    /** Statements scoped to an existing database: BEGIN/COMMIT/ROLLBACK, SELECT, INSERT, UPDATE, DELETE, CREATE/DROP/ALTER TABLE. */
    public Object execute(String databaseName, String query) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty.");
        }
        String normalized = SqlText.collapseWhitespace(query);
        String upper = normalized.toUpperCase();

        // Transaction control statements match on the whole (trimmed) text,
        // not just a prefix -- "BEGIN" and "COMMIT" are complete statements
        // in themselves, unlike "SELECT ..." or "INSERT ...".
        if (upper.equals("BEGIN") || upper.equals("START TRANSACTION")) {
            return executeBegin(databaseName);
        }
        if (upper.equals("COMMIT")) {
            return executeCommit(databaseName);
        }
        if (upper.equals("ROLLBACK")) {
            return executeRollback(databaseName);
        }

        if (upper.startsWith("SELECT")) {
            return executeSelect(databaseName, normalized);
        }
        if (upper.startsWith("INSERT")) {
            return executeInsert(databaseName, normalized);
        }
        if (upper.startsWith("UPDATE")) {
            return executeUpdate(databaseName, normalized);
        }
        if (upper.startsWith("DELETE")) {
            return executeDelete(databaseName, normalized);
        }
        if (upper.startsWith("CREATE TABLE")) {
            return executeCreateTable(databaseName, normalized);
        }
        if (upper.startsWith("DROP TABLE")) {
            return executeDropTable(databaseName, normalized);
        }
        if (upper.startsWith("ALTER TABLE")) {
            return executeAlterTable(databaseName, normalized);
        }
        if (upper.startsWith("CREATE DATABASE") || upper.startsWith("DROP DATABASE")) {
            throw new IllegalArgumentException(
                    "CREATE DATABASE / DROP DATABASE aren't scoped to a database -- run them against POST /api/query instead.");
        }
        throw new IllegalArgumentException("Unsupported query: " + query);
    }

    /** Statements that aren't scoped to any existing database: CREATE DATABASE, DROP DATABASE. */
    public Object executeGlobal(String query) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty.");
        }
        String normalized = SqlText.collapseWhitespace(query);

        Matcher createDb = CREATE_DATABASE_PATTERN.matcher(normalized);
        if (createDb.matches()) {
            String name = createDb.group(1);
            databaseService.createDatabase(name);
            return Map.of("message", "Database created successfully", "database", name);
        }
        Matcher dropDb = DROP_DATABASE_PATTERN.matcher(normalized);
        if (dropDb.matches()) {
            String name = dropDb.group(1);
            databaseService.deleteDatabase(name);
            return Map.of("message", "Database deleted successfully", "database", name);
        }
        throw new IllegalArgumentException("Unsupported query: " + normalized
                + " (only CREATE DATABASE <name> and DROP DATABASE <name> run here -- "
                + "everything else runs against POST /api/databases/{databaseName}/query)");
    }

    /**
     * BEGIN / START TRANSACTION -- starts a session-level transaction for
     * this database. Real MySQL implicitly commits whatever transaction was
     * already open before starting the new one instead of erroring or
     * nesting; we mirror that.
     */
    private Map<String, Object> executeBegin(String databaseName) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName); // validates the database exists
        boolean hadActive = transactionManager.hasActive(databaseName);
        if (hadActive) {
            transactionManager.commitActive(databaseName, databasePath);
        }
        Transaction transaction = transactionManager.beginActive(databaseName);
        String message = hadActive
                ? "Previous transaction committed implicitly. New transaction started. Changes are only visible to others once you COMMIT."
                : "Transaction started. Changes are only visible to others once you COMMIT.";
        return Map.of("message", message, "transactionId", transaction.getId(), "database", databaseName);
    }

    /** COMMIT -- resolves whatever transaction is currently active for this database. A no-op (not an error) if none is, same as MySQL. */
    private Map<String, Object> executeCommit(String databaseName) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        boolean hadActive = transactionManager.hasActive(databaseName);
        transactionManager.commitActive(databaseName, databasePath);
        return Map.of("message", hadActive ? "Transaction committed." : "No transaction was open -- nothing to commit.",
                "database", databaseName);
    }

    /** ROLLBACK -- discards whatever transaction is currently active for this database. A no-op (not an error) if none is, same as MySQL. */
    private Map<String, Object> executeRollback(String databaseName) throws IOException {
        databaseEngine.getDatabasePath(databaseName); // validates the database exists
        boolean hadActive = transactionManager.hasActive(databaseName);
        transactionManager.rollbackActive(databaseName);
        return Map.of("message", hadActive ? "Transaction rolled back. Nothing you staged was ever visible outside it." : "No transaction was open -- nothing to roll back.",
                "database", databaseName);
    }

    /**
     * The single seam that makes INSERT/UPDATE/DELETE/SELECT transaction-
     * aware: if this database has a session transaction open (via
     * executeBegin), every statement transparently joins it -- reading and
     * writing that transaction's private working copy for this table --
     * exactly like running statements inside an open MySQL transaction.
     * With no open transaction, this is just the table's live file, same
     * as before this feature existed.
     */
    private Path resolveDataFile(String databaseName, String tableName, Path tablePath) throws IOException {
        String activeTransactionId = transactionManager.activeTransactionId(databaseName);
        return transactionManager.resolveDataFile(databaseName, tableName, tablePath, activeTransactionId);
    }

    private List<Map<String, Object>> executeSelect(String databaseName, String query) throws IOException {
        String tableName = extractLeadingWordAfter(query, "FROM");
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        tableEngine.getTable(databasePath, tableName); // validates the table exists
        Path tablePath = databasePath.resolve(tableName);
        Path dataFile = resolveDataFile(databaseName, tableName, tablePath);
        return queryEngine.parseSelectQuery(dataFile, tableName, query);
    }

    private Map<String, Object> executeInsert(String databaseName, String query) throws IOException {
        Matcher matcher = INSERT_PATTERN.matcher(query);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported INSERT: " + query + " (expected INSERT INTO <table> (col1, col2) VALUES (val1, val2))");
        }
        String tableName = matcher.group(1);
        List<String> columns = SqlText.splitTopLevel(matcher.group(2), ',');
        List<String> rawValues = SqlText.splitTopLevel(matcher.group(3), ',');
        if (columns.size() != rawValues.size()) {
            throw new IllegalArgumentException(
                    "Column count (" + columns.size() + ") does not match value count (" + rawValues.size() + ").");
        }

        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path dataFile = resolveDataFile(databaseName, tableName, databasePath.resolve(tableName));

        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            row.put(columns.get(i).trim(), SqlValueParser.parseValue(rawValues.get(i)));
        }

        rowService.insertRow(dataFile, row, table);
        return Map.of("message", "Row inserted successfully", "database", databaseName, "table", tableName);
    }

    private Map<String, Object> executeUpdate(String databaseName, String query) throws IOException {
        Matcher matcher = UPDATE_PATTERN.matcher(query);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported UPDATE: " + query
                    + " (expected UPDATE <table> SET col = value [, col2 = value2] [WHERE ...])");
        }
        String tableName = matcher.group(1);
        String[] parts = SqlText.splitOnWhere(matcher.group(2));
        String setClause = parts[0];
        String whereClause = parts[1];

        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        Table table = tableEngine.getTable(databasePath, tableName);
        Path dataFile = resolveDataFile(databaseName, tableName, databasePath.resolve(tableName));

        Map<String, Object> changes = new LinkedHashMap<>();
        for (String assignment : SqlText.splitTopLevel(setClause, ',')) {
            int eq = assignment.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Unsupported SET assignment: " + assignment + " (expected column = value)");
            }
            String column = assignment.substring(0, eq).trim();
            Object value = SqlValueParser.parseValue(assignment.substring(eq + 1).trim());
            changes.put(column, value);
        }
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("UPDATE requires at least one column in SET.");
        }
        dataValidator.validatePartialRecord(changes, table);

        int rowsAffected;
        if (whereClause != null) {
            WhereCondition condition = queryEngine.parseWhereCondition(whereClause);
            rowsAffected = recordStorage.updateWhere(dataFile, table, condition.column(), condition.operator(), condition.value(), changes);
        } else {
            rowsAffected = recordStorage.updateWhere(dataFile, table, null, null, null, changes);
        }
        return Map.of("message", "Update complete", "database", databaseName, "table", tableName, "rowsAffected", rowsAffected);
    }

    private Map<String, Object> executeDelete(String databaseName, String query) throws IOException {
        Matcher matcher = DELETE_PATTERN.matcher(query);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported DELETE: " + query + " (expected DELETE FROM <table> [WHERE ...])");
        }
        String tableName = matcher.group(1);
        String rest = matcher.group(2).trim();

        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        tableEngine.getTable(databasePath, tableName); // validates the table exists
        Path dataFile = resolveDataFile(databaseName, tableName, databasePath.resolve(tableName));

        int rowsAffected;
        if (rest.isEmpty()) {
            rowsAffected = recordStorage.deleteWhere(dataFile, null, null, null);
        } else {
            String[] parts = SqlText.splitOnWhere(rest);
            if (parts[1] == null) {
                throw new IllegalArgumentException("Unsupported DELETE: " + query + " (expected DELETE FROM <table> [WHERE ...])");
            }
            WhereCondition condition = queryEngine.parseWhereCondition(parts[1]);
            rowsAffected = recordStorage.deleteWhere(dataFile, condition.column(), condition.operator(), condition.value());
        }
        return Map.of("message", "Delete complete", "database", databaseName, "table", tableName, "rowsAffected", rowsAffected);
    }

    private Map<String, Object> executeCreateTable(String databaseName, String query) throws IOException {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(query);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported CREATE TABLE: " + query + " (expected CREATE TABLE <name> (col TYPE [PRIMARY KEY], ...))");
        }
        String tableName = matcher.group(1);
        List<ColumnRequest> columns = new ArrayList<>();
        for (String columnDef : SqlText.splitTopLevel(matcher.group(2), ',')) {
            String[] tokens = columnDef.trim().split("\\s+");
            if (tokens.length < 2) {
                throw new IllegalArgumentException(
                        "Unsupported column definition: " + columnDef + " (expected <name> <TYPE> [PRIMARY KEY])");
            }
            ColumnRequest column = new ColumnRequest();
            column.setName(tokens[0]);
            try {
                column.setDataType(DataType.valueOf(tokens[1].toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown data type '" + tokens[1] + "' for column '" + tokens[0]
                        + "'. Expected one of " + Arrays.toString(DataType.values()) + ".");
            }
            String remainder = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length)).toUpperCase();
            column.setPrimaryKey(remainder.contains("PRIMARY") && remainder.contains("KEY"));
            columns.add(column);
        }

        CreateTableRequest request = new CreateTableRequest();
        request.setName(tableName);
        request.setColumns(columns);

        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        implicitlyCommitBeforeDdl(databaseName, databasePath); // DDL implicitly commits an open transaction, same as MySQL
        tableService.createTable(databasePath, request);
        return Map.of("message", "Table created successfully", "database", databaseName, "table", tableName);
    }

    private Map<String, Object> executeDropTable(String databaseName, String query) throws IOException {
        Matcher matcher = DROP_TABLE_PATTERN.matcher(query);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported DROP TABLE: " + query + " (expected DROP TABLE <name>)");
        }
        String tableName = matcher.group(1);
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        implicitlyCommitBeforeDdl(databaseName, databasePath);
        tableService.deleteTable(databasePath, tableName);
        return Map.of("message", "Table deleted successfully", "database", databaseName, "table", tableName);
    }

    /** DDL (CREATE/DROP/ALTER TABLE) implicitly commits whatever transaction is currently open for this database before running -- same as real MySQL, which never lets DDL participate in a transaction. */
    private void implicitlyCommitBeforeDdl(String databaseName, Path databasePath) throws IOException {
        if (transactionManager.hasActive(databaseName)) {
            transactionManager.commitActive(databaseName, databasePath);
        }
    }

    /**
     * ALTER TABLE &lt;table&gt; ADD|DROP|RENAME|MODIFY [COLUMN] ... -- each
     * variant delegates to TableService, which keeps table.meta and every
     * row in data.dat consistent with each other (see TableService's
     * javadoc for why schema is updated before rows are migrated).
     */
    private Map<String, Object> executeAlterTable(String databaseName, String query) throws IOException {
        Path databasePath = databaseEngine.getDatabasePath(databaseName);
        implicitlyCommitBeforeDdl(databaseName, databasePath);

        Matcher add = ALTER_ADD_PATTERN.matcher(query);
        if (add.matches()) {
            String tableName = add.group(1);
            String columnName = add.group(2);
            DataType type = parseDataType(add.group(3), columnName);
            tableService.addColumn(databasePath, tableName, new Column(columnName, type, false));
            return Map.of("message", "Column added successfully", "database", databaseName, "table", tableName, "column", columnName);
        }
        Matcher drop = ALTER_DROP_PATTERN.matcher(query);
        if (drop.matches()) {
            String tableName = drop.group(1);
            String columnName = drop.group(2);
            tableService.dropColumn(databasePath, tableName, columnName);
            return Map.of("message", "Column dropped successfully", "database", databaseName, "table", tableName, "column", columnName);
        }
        Matcher rename = ALTER_RENAME_PATTERN.matcher(query);
        if (rename.matches()) {
            String tableName = rename.group(1);
            String oldName = rename.group(2);
            String newName = rename.group(3);
            tableService.renameColumn(databasePath, tableName, oldName, newName);
            return Map.of("message", "Column renamed successfully", "database", databaseName, "table", tableName, "from", oldName, "to", newName);
        }
        Matcher modify = ALTER_MODIFY_PATTERN.matcher(query);
        if (modify.matches()) {
            String tableName = modify.group(1);
            String columnName = modify.group(2);
            DataType type = parseDataType(modify.group(3), columnName);
            tableService.modifyColumnType(databasePath, tableName, columnName, type);
            return Map.of("message", "Column type changed successfully", "database", databaseName, "table", tableName, "column", columnName, "newType", type.toString());
        }
        throw new IllegalArgumentException("Unsupported ALTER TABLE: " + query
                + " (expected ALTER TABLE <table> ADD|DROP|RENAME|MODIFY [COLUMN] ...)");
    }

    private DataType parseDataType(String raw, String columnName) {
        try {
            return DataType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown data type '" + raw + "' for column '" + columnName
                    + "'. Expected one of " + Arrays.toString(DataType.values()) + ".");
        }
    }

    /** Finds the word right after {@code keyword} (e.g. the table name after "FROM" or "INTO"). */
    private String extractLeadingWordAfter(String query, String keyword) {
        String upper = query.toUpperCase();
        int idx = upper.indexOf(keyword.toUpperCase());
        if (idx < 0) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }
        String remainder = query.substring(idx + keyword.length()).trim();
        Matcher matcher = LEADING_WORD.matcher(remainder);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unsupported query: " + query);
        }
        return matcher.group(1);
    }
}
