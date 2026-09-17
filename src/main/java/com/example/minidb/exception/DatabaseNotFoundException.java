package com.example.minidb.exception;

public class DatabaseNotFoundException extends RuntimeException {
    public DatabaseNotFoundException(String message) {
        super(message);
    }
}
