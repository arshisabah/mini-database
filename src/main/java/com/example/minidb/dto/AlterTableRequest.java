package com.example.minidb.dto;

import com.example.minidb.enums.AlterOperation;
import com.example.minidb.enums.DataType;

/**
 * Body for {@code PATCH /api/databases/{databaseName}/tables/{tableName}}.
 * Which fields are read depends on {@code operation}:
 * <ul>
 *   <li>ADD_COLUMN — {@code column} (name + dataType; primaryKey is ignored, see TableService.addColumn)</li>
 *   <li>DROP_COLUMN — {@code columnName}</li>
 *   <li>RENAME_COLUMN — {@code columnName} (old name), {@code newColumnName}</li>
 *   <li>MODIFY_COLUMN — {@code columnName}, {@code dataType} (the new type)</li>
 * </ul>
 */
public class AlterTableRequest {
    private AlterOperation operation;
    private ColumnRequest column;
    private String columnName;
    private String newColumnName;
    private DataType dataType;

    public AlterOperation getOperation() {
        return operation;
    }

    public void setOperation(AlterOperation operation) {
        this.operation = operation;
    }

    public ColumnRequest getColumn() {
        return column;
    }

    public void setColumn(ColumnRequest column) {
        this.column = column;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getNewColumnName() {
        return newColumnName;
    }

    public void setNewColumnName(String newColumnName) {
        this.newColumnName = newColumnName;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }
}
