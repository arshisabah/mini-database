package com.example.minidb.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class FileStorage {

    public void createDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    public void createFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }
    }

    public void writeText(Path filePath, String content) throws IOException {
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
    }

    public String readText(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return "";
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    public List<String> readLines(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }
        return Files.readAllLines(filePath, StandardCharsets.UTF_8);
    }

    public void appendLine(Path filePath, String line) throws IOException {
        createFile(filePath);
        Files.writeString(filePath, line + System.lineSeparator(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND, java.nio.file.StandardOpenOption.CREATE);
    }

    public void replaceFile(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteFile(Path filePath) throws IOException {
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }

    public void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
