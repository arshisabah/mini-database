package com.example.minidb.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.minidb.engine.DatabaseEngine;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {

    private final DatabaseEngine databaseEngine;

    public DatabaseService(DatabaseEngine databaseEngine) {
        this.databaseEngine = databaseEngine;
    }

    public void createDatabase(String name) throws IOException {
        databaseEngine.createDatabase(name);
    }

    public List<String> listDatabases() throws IOException {
        return databaseEngine.listDatabases();
    }

    public Path getDatabasePath(String databaseName) {
        return databaseEngine.getDatabasePath(databaseName);
    }

    public void deleteDatabase(String databaseName) throws IOException {
        databaseEngine.deleteDatabase(databaseName);
    }
}
