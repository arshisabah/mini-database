package com.example.minidb.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.example.minidb.exception.DatabaseNotFoundException;
import org.springframework.stereotype.Component;

@Component
public class DatabaseStorage {

    private final FileStorage fileStorage = new FileStorage();

    public void createDatabase(Path rootPath, String databaseName) throws IOException {
        Path databasePath = rootPath.resolve(databaseName);
        if (Files.exists(databasePath)) {
            throw new IllegalArgumentException("Database already exists: " + databaseName);
        }
        fileStorage.createDirectories(databasePath);
        fileStorage.writeText(databasePath.resolve("database.meta"), "{\"name\":\"" + databaseName + "\"}\n");
    }

    public List<String> listDatabases(Path rootPath) throws IOException {
        if (!Files.exists(rootPath)) {
            return new ArrayList<>();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(rootPath)) {
            stream.filter(Files::isDirectory)
                  .forEach(path -> names.add(path.getFileName().toString()));
        }
        return names;
    }

    public Path getDatabasePath(Path rootPath, String databaseName) {
        Path path = rootPath.resolve(databaseName).normalize();
        if (!path.startsWith(rootPath.normalize())) {
            throw new IllegalArgumentException("Invalid database name: " + databaseName);
        }
        if (!Files.exists(path)) {
            throw new DatabaseNotFoundException("Database '" + databaseName + "' not found");
        }
        return path;
    }

    public void deleteDatabase(Path rootPath, String databaseName) throws IOException {
        Path databasePath = getDatabasePath(rootPath, databaseName);
        fileStorage.deleteDirectory(databasePath);
    }
}
