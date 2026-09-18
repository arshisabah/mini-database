package com.example.minidb.engine;

/**
 * Turns a raw token from a typed query (e.g. "21", "true", "'CSE'") into the
 * Java type it should be stored/compared as. Shared by every statement type
 * so "age = 21" and "INSERT INTO ... VALUES (21, ...)" agree on what "21"
 * means.
 */
public final class SqlValueParser {

    private SqlValueParser() {
    }

    public static Object parseValue(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("null")) {
            return null;
        }
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(trimmed);
        }
        if (trimmed.matches("-?\\d+")) {
            return Integer.parseInt(trimmed);
        }
        if (trimmed.matches("-?\\d+\\.\\d+")) {
            return Double.parseDouble(trimmed);
        }
        // Bare, single-, or double-quoted strings are all accepted — quotes
        // are optional in this small dialect ("CSE", 'CSE', and "'CSE'" all
        // resolve to the string CSE).
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
