package com.example.minidb.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.minidb.dto.ColumnRequest;
import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.enums.DataType;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.example.minidb.storage.TableStorage;
import com.example.minidb.validator.DataValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TableEngine {

    private final TableStorage tableStorage;
    private final DataValidator dataValidator = new DataValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TableEngine(TableStorage tableStorage) {
        this.tableStorage = tableStorage;
    }

    public void createTable(Path databasePath, CreateTableRequest request) throws IOException {
        validateRequest(request);
        tableStorage.createTable(databasePath, request);
    }

    public List<String> listTables(Path databasePath) throws IOException {
        return tableStorage.listTables(databasePath);
    }

    public Table getTable(Path databasePath, String tableName) throws IOException {
        Path tablePath = tableStorage.getTablePath(databasePath, tableName);
        String metaContent = tableStorage.readTableMeta(tablePath);
        if (metaContent == null || metaContent.isBlank()) {
            throw new IllegalArgumentException("Table metadata is empty for table '" + tableName + "'.");
        }
        Table table = objectMapper.readValue(metaContent, Table.class);
        if (table.getColumns() == null || table.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Table metadata is invalid for table '" + tableName + "'.");
        }
        return table;
    }

    public void deleteTable(Path databasePath, String tableName) throws IOException {
        tableStorage.deleteTable(databasePath, tableName);
    }

    // -----------------------------------------------------------------
    // ALTER TABLE -- schema mutation only. Row migration (stripping a
    // dropped column from every row, renaming a key in every row) is
    // TableService's job, since that needs RecordStorage, which TableEngine
    // deliberately doesn't depend on -- this class stays metadata-only.
    // Each method re-validates the whole resulting schema with
    // DataValidator before writing it, so the same rules that apply to
    // CREATE TABLE (valid names, no duplicates, exactly one primary key)
    // still hold after an ALTER.
    // -----------------------------------------------------------------

    public Table addColumn(Path databasePath, String tableName, Column newColumn) throws IOException {
        Path tablePath = tableStorage.getTablePath(databasePath, tableName);
        Table table = getTable(databasePath, tableName);
        if (table.getColumns().stream().anyMatch(c -> c.getName().equalsIgnoreCase(newColumn.getName()))) {
            throw new IllegalArgumentException("Column already exists: " + newColumn.getName());
        }
        if (newColumn.isPrimaryKey()) {
            throw new IllegalArgumentException(
                    "ALTER TABLE ADD COLUMN can't introduce a new primary key -- the table already has one.");
        }
        List<Column> columns = new ArrayList<>(table.getColumns());
        columns.add(newColumn);
        Table updated = new Table(table.getName(), columns);
        dataValidator.validateTableDefinition(updated);
        tableStorage.writeTableMeta(tablePath, updated);
        return updated;
    }

    public Table dropColumn(Path databasePath, String tableName, String columnName) throws IOException {
        Path tablePath = tableStorage.getTablePath(databasePath, tableName);
        Table table = getTable(databasePath, tableName);
        Column target = table.getColumns().stream()
                .filter(c -> c.getName().equalsIgnoreCase(columnName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown column: '" + columnName + "'."));
        if (target.isPrimaryKey()) {
            throw new IllegalArgumentException("Can't drop the primary key column '" + columnName + "'.");
        }
        List<Column> columns = new ArrayList<>();
        for (Column c : table.getColumns()) {
            if (!c.getName().equalsIgnoreCase(columnName)) {
                columns.add(c);
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("A table must keep at least one column.");
        }
        Table updated = new Table(table.getName(), columns);
        dataValidator.validateTableDefinition(updated);
        tableStorage.writeTableMeta(tablePath, updated);
        return updated;
    }

    public Table renameColumn(Path databasePath, String tableName, String oldName, String newName) throws IOException {
        Path tablePath = tableStorage.getTablePath(databasePath, tableName);
        Table table = getTable(databasePath, tableName);
        if (!DataValidator.isValidName(newName)) {
            throw new IllegalArgumentException("Invalid column name: " + newName);
        }
        boolean found = false;
        List<Column> columns = new ArrayList<>();
        for (Column c : table.getColumns()) {
            if (c.getName().equalsIgnoreCase(oldName)) {
                found = true;
                columns.add(new Column(newName, c.getDataType(), c.isPrimaryKey()));
            } else {
                if (c.getName().equalsIgnoreCase(newName)) {
                    throw new IllegalArgumentException("Column already exists: " + newName);
                }
                columns.add(c);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown column: '" + oldName + "'.");
        }
        Table updated = new Table(table.getName(), columns);
        dataValidator.validateTableDefinition(updated);
        tableStorage.writeTableMeta(tablePath, updated);
        return updated;
    }

    /**
     * Changes a column's declared type going forward. This deliberately does
     * NOT attempt to convert existing stored values to the new type -- rows
     * written before the change keep whatever they had, which may no longer
     * satisfy the new type on a future read-and-validate. That's a known,
     * documented limitation (see README), not an oversight: safe automatic
     * type coercion (e.g. VARCHAR "abc" -> INT) isn't generally possible.
     */
    public Table modifyColumnType(Path databasePath, String tableName, String columnName, DataType newType) throws IOException {
        Path tablePath = tableStorage.getTablePath(databasePath, tableName);
        Table table = getTable(databasePath, tableName);
        boolean found = false;
        List<Column> columns = new ArrayList<>();
        for (Column c : table.getColumns()) {
            if (c.getName().equalsIgnoreCase(columnName)) {
                found = true;
                columns.add(new Column(c.getName(), newType, c.isPrimaryKey()));
            } else {
                columns.add(c);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown column: '" + columnName + "'.");
        }
        Table updated = new Table(table.getName(), columns);
        dataValidator.validateTableDefinition(updated);
        tableStorage.writeTableMeta(tablePath, updated);
        return updated;
    }

    private void validateRequest(CreateTableRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Table request is required.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Table name is required.");
        }
        if (!DataValidator.isValidName(request.getName())) {
            throw new IllegalArgumentException("Invalid table name: " + request.getName());
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Table must define at least one column.");
        }

        List<Column> converted = new ArrayList<>();
        int primaryKeyCount = 0;
        for (ColumnRequest columnRequest : request.getColumns()) {
            if (columnRequest == null || columnRequest.getName() == null || columnRequest.getName().isBlank()) {
                throw new IllegalArgumentException("Each column must have a name.");
            }
            if (columnRequest.getDataType() == null) {
                throw new IllegalArgumentException("Column '" + columnRequest.getName() + "' has no data type.");
            }
            if (!DataValidator.isValidName(columnRequest.getName())) {
                throw new IllegalArgumentException("Invalid column name: " + columnRequest.getName());
            }
            if (columnRequest.isPrimaryKey()) {
                primaryKeyCount++;
            }
            converted.add(new Column(columnRequest.getName(), columnRequest.getDataType(), columnRequest.isPrimaryKey()));
        }
        if (primaryKeyCount != 1) {
            throw new IllegalArgumentException("Table must declare exactly one primary key.");
        }

        Table table = new Table(request.getName(), converted);
        dataValidator.validateTableDefinition(table);
    }
}
