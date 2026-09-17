package com.example.minidb.dto;

import java.util.List;

public class CreateTableRequest {
    private String name;
    private List<ColumnRequest> columns;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ColumnRequest> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnRequest> columns) {
        this.columns = columns;
    }
}
