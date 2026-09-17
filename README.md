# MiniDB

MiniDB is a beginner-friendly file-based database engine built with Java 17 and Spring Boot. It stores data on the local filesystem instead of using MySQL, PostgreSQL, SQLite, or any other external database system.

The project is meant to teach the fundamentals of:

- databases
- tables
- columns
- metadata
- records
- persistence
- validation
- simple filtering

## Tech stack

- Java 17
- Spring Boot 3.x
- Spring Web
- Maven
- Jackson
- Java NIO File API
- Java Collections

## Project goal

MiniDB lets you create databases and tables through REST APIs and persist the data to files in a local folder.

The source of truth is the file system, not an in-memory collection.

## Storage layout

By default, MiniDB stores data under:

```text
./minidb-data
```

Example structure:

```text
minidb-data/
└── databases/
    └── college/
        ├── database.meta
        └── students/
            ├── table.meta
            └── data.dat
```

### What each file does

- `database.meta` stores database metadata.
- `table.meta` stores table metadata such as column names, types, and the primary key.
- `data.dat` stores actual row records, one JSON object per line.

## Configuration

File: `src/main/resources/application.properties`

```properties
server.port=8080
minidb.storage.path=./minidb-data
```

Changing `minidb.storage.path` changes where MiniDB stores its files.

## Run the project

From the project root:

```bash
mvn spring-boot:run
```

Then open the API on:

```text
http://localhost:8080
```

## Main API endpoints

### Database endpoints

```http
POST /api/databases
GET /api/databases
GET /api/databases/{databaseName}
DELETE /api/databases/{databaseName}
```

Example create database request:

```json
{
  "name": "college"
}
```

### Table endpoints

```http
POST /api/databases/{databaseName}/tables
GET /api/databases/{databaseName}/tables
GET /api/databases/{databaseName}/tables/{tableName}
DELETE /api/databases/{databaseName}/tables/{tableName}
```

Example create table request:

```json
{
  "name": "students",
  "columns": [
    { "name": "id", "dataType": "INT", "primaryKey": true },
    { "name": "name", "dataType": "VARCHAR", "primaryKey": false },
    { "name": "age", "dataType": "INT", "primaryKey": false },
    { "name": "department", "dataType": "VARCHAR", "primaryKey": false }
  ]
}
```

### Row endpoints

```http
POST /api/databases/{databaseName}/tables/{tableName}/rows
GET /api/databases/{databaseName}/tables/{tableName}/rows
GET /api/databases/{databaseName}/tables/{tableName}/rows/{id}
PUT /api/databases/{databaseName}/tables/{tableName}/rows/{id}
DELETE /api/databases/{databaseName}/tables/{tableName}/rows/{id}
```

Example row insert:

```json
{
  "id": 1,
  "name": "Rahul",
  "age": 21,
  "department": "CSE"
}
```

### Filter example

```http
GET /api/databases/college/tables/students/rows?column=age&operator=GT&value=20
```

Supported operators:

- EQ
- GT
- LT
- GTE
- LTE

## Persistence behavior

MiniDB persists records to `data.dat` using JSON lines.

Each record is stored as one line, for example:

```json
{"id":1,"name":"Rahul","age":21,"department":"CSE"}
```

When the application stops and starts again, the data remains on disk and is read again from the file system.

## Validation

MiniDB validates:

- unknown columns
- invalid data types
- missing primary key values
- duplicate primary key values
- invalid table and column names

## Notes

This is a simplified educational database engine. It intentionally does not include:

- SQL joins
- transactions
- indexes
- advanced query optimization
- external database systems

It is designed to help beginners understand how a real database engine can use files, metadata, and validation to manage stored records.

## Example workflow

1. Create database
2. Create table
3. Insert rows
4. Read rows
5. Update a row
6. Delete a row
7. Restart the app
8. Verify data still exists

## Main package structure

```text
src/main/java/com/example/minidb/
├── controller/
├── service/
├── engine/
├── storage/
├── model/
├── dto/
├── enums/
├── validator/
├── exception/
├── config/
├── MiniDbApplication.java
└──
```

## License

This project is for educational and learning purposes.
