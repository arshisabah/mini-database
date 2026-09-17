package com.example.minidb.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public Path minidbStoragePath(@Value("${minidb.storage.path:./minidb-data}") String storagePath) {
        Path resolvedPath = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(resolvedPath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create MiniDB storage directory: " + resolvedPath, e);
        }
        return resolvedPath;
    }
}
