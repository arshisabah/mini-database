package com.example.minidb.validator;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.minidb.enums.DataType;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;

public class DataValidator {

    public void validateTableDefinition(Table table) {
        if (table == null || table.getName() == null || table.getName().isBlank()) {
            throw new IllegalArgumentException("Table name is required.");
        }
        if (!isValidName(table.getName())) {
            throw new IllegalArgumentException("Invalid table name: " + table.getName());
        }
        if (table.getColumns() == null || table.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Table must define at least one column.");
        }

        Set<String> names = new HashSet<>();
        boolean hasPrimaryKey = false;
        for (Column column : table.getColumns()) {
            if (column == null || column.getName() == null || column.getName().isBlank()) {
                throw new IllegalArgumentException("Each column must have a valid name.");
            }
            if (!isValidName(column.getName())) {
                throw new IllegalArgumentException("Invalid column name: " + column.getName());
            }
            if (!names.add(column.getName().toLowerCase())) {
                throw new IllegalArgumentException("Duplicate column name: " + column.getName());
            }
            if (column.getDataType() == null) {
                throw new IllegalArgumentException("Column '" + column.getName() + "' must define a data type.");
            }
            if (column.isPrimaryKey()) {
                hasPrimaryKey = true;
            }
        }
        if (!hasPrimaryKey) {
            throw new IllegalArgumentException("Table must declare exactly one primary key.");
        }
    }

    public void validateRecord(Map<String, Object> record, Table table) {
        validateRecord(record, table, true);
    }

    public void validatePartialRecord(Map<String, Object> record, Table table) {
        validateRecord(record, table, false);
    }

    private void validateRecord(Map<String, Object> record, Table table, boolean requireAllFields) {
        if (record == null) {
            throw new IllegalArgumentException("Record cannot be null.");
        }

        List<Column> columns = table.getColumns();
        for (String key : record.keySet()) {
            if (columns.stream().noneMatch(c -> c.getName().equals(key))) {
                throw new IllegalArgumentException("Unknown column: '" + key + "'.");
            }
        }

        for (Column column : columns) {
            Object value = record.get(column.getName());
            if (value == null) {
                if (requireAllFields && column.isPrimaryKey()) {
                    throw new IllegalArgumentException("Missing required primary key column '" + column.getName() + "'.");
                }
                if (requireAllFields && !column.isPrimaryKey()) {
                    throw new IllegalArgumentException("Missing required column '" + column.getName() + "'.");
                }
                continue;
            }
            validateValue(column, value);
        }
    }

    public void validateValue(Column column, Object value) {
        if (column == null || column.getDataType() == null) {
            throw new IllegalArgumentException("Column definition is invalid.");
        }

        DataType type = column.getDataType();
        if (type == DataType.INT) {
            if (!(value instanceof Number) || value instanceof Double || value instanceof Float) {
                throw new IllegalArgumentException("Invalid value for column '" + column.getName() + "'. Expected INT.");
            }
            return;
        }
        if (type == DataType.VARCHAR) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Invalid value for column '" + column.getName() + "'. Expected VARCHAR.");
            }
            return;
        }
        if (type == DataType.DOUBLE) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Invalid value for column '" + column.getName() + "'. Expected DOUBLE.");
            }
            return;
        }
        if (type == DataType.BOOLEAN) {
            if (!(value instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid value for column '" + column.getName() + "'. Expected BOOLEAN.");
            }
            return;
        }
        if (type == DataType.DATE) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Invalid value for column '" + column.getName() + "'. Expected DATE.");
            }
            try {
                LocalDate.parse((String) value);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid value for column '" + column.getName() + "'. Expected DATE.");
            }
        }
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return name.matches("[a-zA-Z0-9_]+") && !name.contains("../") && !name.contains("..\\") && !name.contains("/") && !name.contains("\\");
    }
}
