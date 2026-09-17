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

### Transaction endpoints (commit / rollback)

```http
POST /api/databases/{databaseName}/tables/{tableName}/transactions
GET /api/databases/{databaseName}/tables/{tableName}/transactions/{transactionId}
POST /api/databases/{databaseName}/tables/{tableName}/transactions/{transactionId}/commit
POST /api/databases/{databaseName}/tables/{tableName}/transactions/{transactionId}/rollback
```

Begin a transaction to get a `transactionId`, then pass it as a query param
on any row insert/update/delete/read for that table:

```http
POST /api/databases/college/tables/students/rows?transactionId=<id>
```

Those requests only touch a private staging copy of the table's data —
nothing changes in the live table until you `commit`. `rollback` discards
the staging copy instead, leaving the live table exactly as it was. See
`TransactionManager` for how staging and the atomic commit swap work.

### Filter example

```http
GET /api/databases/college/tables/students/rows?column=age&operator=GT&value=20
```

Supported operators:

- EQ
- NEQ
- GT
- LT
- GTE
- LTE

### Query console

```http
POST /api/databases/college/query
{ "query": "SELECT * FROM students WHERE age > 20" }
```

The `WHERE` clause accepts standard MySQL-style comparison operators
directly — `=`, `!=`, `<>`, `>`, `<`, `>=`, `<=` — with spaces around the
operator optional, e.g. `age>20` works the same as `age > 20`. String
values can be bare, single-quoted, or double-quoted:
`department = CSE`, `department = 'CSE'`, and `department = "CSE"` are all
equivalent. Only one condition is supported (no `AND`/`OR`).

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
- indexes
- advanced query optimization
- external database systems

It does include a lightweight, table-scoped commit/rollback mechanism (see
"Transaction endpoints" above) — not full ACID transactions with isolation
levels or cross-table atomicity, but a real staging-file-then-atomic-swap
model, not just a client-side illusion.

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
