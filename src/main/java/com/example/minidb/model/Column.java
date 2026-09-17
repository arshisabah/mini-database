package com.example.minidb.model;

import com.example.minidb.enums.DataType;

public class Column {

    private String name;
    private DataType dataType;
    private boolean primaryKey;

    public Column() {
    }

    public Column(String name, DataType dataType, boolean primaryKey) {
        this.name = name;
        this.dataType = dataType;
        this.primaryKey = primaryKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }
}
