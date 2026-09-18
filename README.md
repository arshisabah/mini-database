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

### If you serve Frontend/ with VS Code's Live Server

Live Server watches your whole workspace and auto-refreshes the browser on
any file change. MiniDB writes to `minidb-data/` on every database/table/row
operation, and Maven writes to `target/` on every build — with a normal
Live Server setup, both look like workspace changes, so the page force-
reloads right after you do practically anything. That reload is what a
lost connection looks like too (state resets, "Not connected" appears
briefly, then the page's own startup logic reconnects a second or two
later) — it's easy to mistake for a backend bug.

This repo ships a `.vscode/settings.json` (in both the project root and
`Frontend/`, since it depends on which folder you actually opened as your
VS Code workspace) that tells Live Server to ignore `minidb-data/` and
`target/`. If you already had Live Server running when you pulled these
changes, stop it and click "Go Live" again — the ignore list is only read
when the server (re)starts.

If it's still reloading after that, the fully reliable fix is to move
MiniDB's storage outside anything Live Server could ever be watching, by
overriding `minidb.storage.path` in `application.properties`:

```properties
minidb.storage.path=${user.home}/minidb-data
```

That keeps your data in your home directory instead of inside the
project, so no workspace-watching tool can ever see it change.

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

The query console runs full SQL-text statements — not just `SELECT` — routed
to the same services the structured REST endpoints use. Every statement
here executes immediately against the live data (autocommit-style, no
staged/review step); use the row-editing UI or `TransactionController`
directly if you want to stage changes first.

Scoped to one database — `POST /api/databases/{databaseName}/query`:

```http
POST /api/databases/college/query
{ "query": "SELECT * FROM students WHERE age > 20" }
{ "query": "INSERT INTO students (id, name, age, department) VALUES (5, 'Meera', 22, 'ECE')" }
{ "query": "UPDATE students SET age = 23 WHERE id = 5" }
{ "query": "DELETE FROM students WHERE id = 5" }
{ "query": "CREATE TABLE staff (id INT PRIMARY KEY, name VARCHAR)" }
{ "query": "DROP TABLE staff" }
{ "query": "ALTER TABLE staff ADD COLUMN email VARCHAR" }
{ "query": "ALTER TABLE staff DROP COLUMN email" }
{ "query": "ALTER TABLE staff RENAME COLUMN email TO contact_email" }
{ "query": "ALTER TABLE staff MODIFY COLUMN age DOUBLE" }
```

Not scoped to any existing database — `POST /api/query`:

```http
POST /api/query
{ "query": "CREATE DATABASE shop_db" }
{ "query": "DROP DATABASE shop_db" }
```

The `WHERE` clause (SELECT/UPDATE/DELETE) accepts standard MySQL-style
comparison operators directly — `=`, `!=`, `<>`, `>`, `<`, `>=`, `<=` — with
spaces around the operator optional, e.g. `age>20` works the same as
`age > 20`. String values can be bare, single-quoted, or double-quoted:
`department = CSE`, `department = 'CSE'`, and `department = "CSE"` are all
equivalent. `UPDATE`/`DELETE` with no `WHERE` applies to every row in the
table, same as real SQL. Only a single condition is supported (no
`AND`/`OR`).

`ALTER TABLE` supports `ADD COLUMN`, `DROP COLUMN`, `RENAME COLUMN ... TO
...`, and `MODIFY COLUMN` (`COLUMN` is optional in each — `ADD email
VARCHAR` works too). The same operations are available as REST via
`PATCH /api/databases/{databaseName}/tables/{tableName}` (see
`AlterTableRequest`). Adding a column needs no data migration — a row that
doesn't have it yet already reads as `null` everywhere in MiniDB. Dropping
a column removes it from every existing row; renaming renames the key in
every row. `MODIFY COLUMN` only changes the type going forward — it does
**not** convert existing rows' values, so a row written before the change
keeps whatever it had. You can't drop or rename away the primary key, and
`ADD COLUMN` can't introduce a second one.

Response shape: `SELECT` returns a JSON array of rows, same as before.
Every other statement returns `{"message": "...", "rowsAffected": N}` (no
`rowsAffected` for `CREATE`/`DROP`, since those don't touch rows).

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
- `AND`/`OR` or multi-condition `WHERE` clauses
- `ALTER TABLE ... MODIFY` converting existing rows' values to a new type (the schema changes; stored values don't)
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
