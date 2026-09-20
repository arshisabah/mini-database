package com.example.minidb.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-rolled text helpers shared by every statement parser in SqlEngine and
 * QueryEngine. Nothing here understands SQL grammar in general — each is a
 * narrow tool for one recurring job in this project's small dialect.
 */
public final class SqlText {

    private SqlText() {
    }

    /**
     * Trims, collapses internal whitespace runs to single spaces, and drops
     * one trailing statement-terminating semicolon if present -- so typing
     * "BEGIN;" or "SELECT * FROM students;" out of MySQL habit works the
     * same as leaving it off. Safe to do unconditionally: this project
     * doesn't support multiple statements in one call, so a lone trailing
     * `;` can only ever be a terminator, never content -- a semicolon
     * inside a quoted value is never the last character of the whole
     * query, because the closing quote (or paren) comes after it.
     */
    public static String collapseWhitespace(String text) {
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * Splits {@code text} on the first standalone "WHERE" keyword (case
     * insensitive) that isn't inside a quoted string. Returns
     * {beforeWhere, afterWhereOrNull} — the second element is null when
     * there's no WHERE clause at all.
     */
    public static String[] splitOnWhere(String text) {
        int idx = indexOfKeyword(text, "WHERE");
        if (idx < 0) {
            return new String[]{text.trim(), null};
        }
        return new String[]{text.substring(0, idx).trim(), text.substring(idx + "WHERE".length()).trim()};
    }

    /** Case-insensitive search for a whole-word keyword, skipping quoted regions. */
    private static int indexOfKeyword(String text, String keyword) {
        String upper = text.toUpperCase();
        String upperKeyword = keyword.toUpperCase();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i <= upper.length() - upperKeyword.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (upper.startsWith(upperKeyword, i)) {
                boolean leftBoundary = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
                int after = i + upperKeyword.length();
                boolean rightBoundary = after >= text.length() || !Character.isLetterOrDigit(text.charAt(after));
                if (leftBoundary && rightBoundary) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Splits {@code text} on top-level occurrences of {@code delimiter} —
     * i.e. not inside single/double quotes and not inside parentheses.
     * Used for column lists, value lists, and comma-separated SET
     * assignments, all of which may contain quoted strings with commas
     * inside them (e.g. {@code 'Smith, John'}).
     */
    public static List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
            }
            if (c == delimiter && !inSingle && !inDouble && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty() || !parts.isEmpty()) {
            parts.add(current.toString().trim());
        }
        return parts;
    }
}
