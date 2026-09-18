package com.example.minidb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.enums.DataType;
import com.example.minidb.engine.TableEngine;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.example.minidb.storage.RecordStorage;
import org.springframework.stereotype.Service;

@Service
public class TableService {

    private final TableEngine tableEngine;
    private final RecordStorage recordStorage;

    public TableService(TableEngine tableEngine, RecordStorage recordStorage) {
        this.tableEngine = tableEngine;
        this.recordStorage = recordStorage;
    }

    public void createTable(Path databasePath, CreateTableRequest request) throws IOException {
        tableEngine.createTable(databasePath, request);
    }

    public List<String> listTables(Path databasePath) throws IOException {
        return tableEngine.listTables(databasePath);
    }

    public Table getTable(Path databasePath, String tableName) throws IOException {
        return tableEngine.getTable(databasePath, tableName);
    }

    public void deleteTable(Path databasePath, String tableName) throws IOException {
        tableEngine.deleteTable(databasePath, tableName);
    }

    // -----------------------------------------------------------------
    // ALTER TABLE -- coordinates the schema change (TableEngine, writes
    // table.meta) with the matching row migration (RecordStorage, rewrites
    // data.dat) so the two files stay consistent with each other. Schema is
    // updated first: TableEngine's validation (valid names, no duplicate
    // columns, can't drop the primary key, etc.) is what should reject a
    // bad request, and it should do that before any row data is touched.
    // -----------------------------------------------------------------

    /** Adding a column never needs to touch existing rows: a row simply
     *  reading as null for a column it doesn't have yet is already correct
     *  everywhere in MiniDB (see RecordStorage/DataValidator), so there's
     *  nothing to migrate. */
    public Table addColumn(Path databasePath, String tableName, Column newColumn) throws IOException {
        return tableEngine.addColumn(databasePath, tableName, newColumn);
    }

    public Table dropColumn(Path databasePath, String tableName, String columnName) throws IOException {
        Table updated = tableEngine.dropColumn(databasePath, tableName, columnName);
        Path dataFile = databasePath.resolve(tableName).resolve("data.dat");
        recordStorage.dropColumnFromRows(dataFile, columnName);
        return updated;
    }

    public Table renameColumn(Path databasePath, String tableName, String oldName, String newName) throws IOException {
        Table updated = tableEngine.renameColumn(databasePath, tableName, oldName, newName);
        Path dataFile = databasePath.resolve(tableName).resolve("data.dat");
        recordStorage.renameColumnInRows(dataFile, oldName, newName);
        return updated;
    }

    public Table modifyColumnType(Path databasePath, String tableName, String columnName, DataType newType) throws IOException {
        return tableEngine.modifyColumnType(databasePath, tableName, columnName, newType);
    }
}
