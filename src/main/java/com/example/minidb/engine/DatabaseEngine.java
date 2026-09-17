package com.example.minidb.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.example.minidb.exception.DatabaseNotFoundException;
import com.example.minidb.storage.DatabaseStorage;
import org.springframework.stereotype.Component;

@Component
public class DatabaseEngine {

    private final DatabaseStorage databaseStorage;
    private final Path storageRoot;

    public DatabaseEngine(DatabaseStorage databaseStorage, Path storageRoot) {
        this.databaseStorage = databaseStorage;
        this.storageRoot = storageRoot;
    }

    public void createDatabase(String databaseName) throws IOException {
        validateName(databaseName);
        databaseStorage.createDatabase(storageRoot.resolve("databases"), databaseName);
    }

    public List<String> listDatabases() throws IOException {
        return databaseStorage.listDatabases(storageRoot.resolve("databases"));
    }

    public Path getDatabasePath(String databaseName) {
        return databaseStorage.getDatabasePath(storageRoot.resolve("databases"), databaseName);
    }

    public void deleteDatabase(String databaseName) throws IOException {
        validateName(databaseName);
        databaseStorage.deleteDatabase(storageRoot.resolve("databases"), databaseName);
    }

    public void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Database name is required.");
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid database name: " + name);
        }
        if (name.contains("../") || name.contains("..\\") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Invalid database name: " + name);
        }
    }
}
