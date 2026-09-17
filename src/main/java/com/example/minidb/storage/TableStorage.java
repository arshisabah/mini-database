package com.example.minidb.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.example.minidb.dto.ColumnRequest;
import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.exception.TableNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TableStorage {

    private final FileStorage fileStorage = new FileStorage();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void createTable(Path databasePath, CreateTableRequest request) throws IOException {
        Path tablePath = databasePath.resolve(request.getName());
        if (Files.exists(tablePath)) {
            throw new IllegalArgumentException("Table already exists: " + request.getName());
        }
        fileStorage.createDirectories(tablePath);
        fileStorage.writeText(tablePath.resolve("table.meta"), objectMapper.writeValueAsString(request));
        fileStorage.createFile(tablePath.resolve("data.dat"));
    }

    public List<String> listTables(Path databasePath) throws IOException {
        if (!Files.exists(databasePath)) {
            return new ArrayList<>();
        }
        List<String> tables = new ArrayList<>();
        try (Stream<Path> stream = Files.list(databasePath)) {
            stream.filter(Files::isDirectory)
                  .forEach(path -> tables.add(path.getFileName().toString()));
        }
        return tables;
    }

    public Path getTablePath(Path databasePath, String tableName) {
        Path path = databasePath.resolve(tableName).normalize();
        if (!path.startsWith(databasePath.normalize())) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        if (!Files.exists(path)) {
            throw new TableNotFoundException("Table '" + tableName + "' not found");
        }
        return path;
    }

    public String readTableMeta(Path tablePath) throws IOException {
        return fileStorage.readText(tablePath.resolve("table.meta"));
    }

    public void deleteTable(Path databasePath, String tableName) throws IOException {
        Path tablePath = getTablePath(databasePath, tableName);
        fileStorage.deleteDirectory(tablePath);
    }
}
