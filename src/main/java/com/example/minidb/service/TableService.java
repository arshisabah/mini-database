package com.example.minidb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.engine.TableEngine;
import com.example.minidb.model.Table;
import org.springframework.stereotype.Service;

@Service
public class TableService {

    private final TableEngine tableEngine;

    public TableService(TableEngine tableEngine) {
        this.tableEngine = tableEngine;
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
}
