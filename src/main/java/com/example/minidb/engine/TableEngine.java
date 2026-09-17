package com.example.minidb.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.example.minidb.dto.ColumnRequest;
import com.example.minidb.dto.CreateTableRequest;
import com.example.minidb.enums.DataType;
import com.example.minidb.model.Column;
import com.example.minidb.model.Table;
import com.example.minidb.storage.TableStorage;
import com.example.minidb.validator.DataValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TableEngine {

    private final TableStorage tableStorage;
    private final DataValidator dataValidator = new DataValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TableEngine(TableStorage tableStorage) {
        this.tableStorage = tableStorage;
    }

    public void createTable(Path databasePath, CreateTableRequest request) throws IOException {
        validateRequest(request);
        tableStorage.createTable(databasePath, request);
    }

    public List<String> listTables(Path databasePath) throws IOException {
        return tableStorage.listTables(databasePath);
    }

    public Table getTable(Path databasePath, String tableName) throws IOException {
        Path tablePath = tableStorage.getTablePath(databasePath, tableName);
        String metaContent = tableStorage.readTableMeta(tablePath);
        if (metaContent == null || metaContent.isBlank()) {
            throw new IllegalArgumentException("Table metadata is empty for table '" + tableName + "'.");
        }
        Table table = objectMapper.readValue(metaContent, Table.class);
        if (table.getColumns() == null || table.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Table metadata is invalid for table '" + tableName + "'.");
        }
        return table;
    }

    public void deleteTable(Path databasePath, String tableName) throws IOException {
        tableStorage.deleteTable(databasePath, tableName);
    }

    private void validateRequest(CreateTableRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Table request is required.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Table name is required.");
        }
        if (!DataValidator.isValidName(request.getName())) {
            throw new IllegalArgumentException("Invalid table name: " + request.getName());
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new IllegalArgumentException("Table must define at least one column.");
        }

        List<Column> converted = new ArrayList<>();
        int primaryKeyCount = 0;
        for (ColumnRequest columnRequest : request.getColumns()) {
            if (columnRequest == null || columnRequest.getName() == null || columnRequest.getName().isBlank()) {
                throw new IllegalArgumentException("Each column must have a name.");
            }
            if (columnRequest.getDataType() == null) {
                throw new IllegalArgumentException("Column '" + columnRequest.getName() + "' has no data type.");
            }
            if (!DataValidator.isValidName(columnRequest.getName())) {
                throw new IllegalArgumentException("Invalid column name: " + columnRequest.getName());
            }
            if (columnRequest.isPrimaryKey()) {
                primaryKeyCount++;
            }
            converted.add(new Column(columnRequest.getName(), columnRequest.getDataType(), columnRequest.isPrimaryKey()));
        }
        if (primaryKeyCount != 1) {
            throw new IllegalArgumentException("Table must declare exactly one primary key.");
        }

        Table table = new Table(request.getName(), converted);
        dataValidator.validateTableDefinition(table);
    }
}
