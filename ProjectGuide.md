# Build a Beginner-Friendly Mini Database Engine from Scratch

Act as a **senior Java backend engineer and database systems engineer**.

Build a complete beginner-friendly project called **MiniDB**.

The goal is to create a **small database management system from scratch using Java 17 and Spring Boot**.

This project should teach the fundamentals of how a database stores and manages data.

## VERY IMPORTANT

This project must have its **OWN STORAGE SYSTEM**.

The application must NOT use:

* MySQL
* PostgreSQL
* MongoDB
* SQLite
* H2
* JDBC
* JPA
* Hibernate
* Spring Data JPA
* Any external database

The data must be physically stored by **MiniDB itself on the local file system**.

Spring Boot should only be used to expose REST APIs and manage the application.

The database engine itself must handle:

```text
Database creation
Table creation
Column definitions
Record insertion
Record reading
Record updating
Record deletion
Basic filtering
Data validation
File persistence
```

Keep everything **simple and beginner-friendly**.

Do NOT try to recreate the entire MySQL database engine.

---

# 1. Technology Stack

Use only:

* Java 17
* Spring Boot 3.x
* Spring Web
* Maven
* Jackson
* Java NIO File API
* Java Collections

Do NOT use:

```text
MySQL
PostgreSQL
MongoDB
SQLite
H2
JPA
Hibernate
JDBC
Spring Data
Redis
Kafka
Docker
JWT
Spring Security
Microservices
GraphQL
```

---

# 2. Main Goal

Create a database engine called:

```text
MiniDB
```

A user should be able to interact with it through REST APIs.

The user should be able to:

```text
Create database
List databases
Delete database

Create table
List tables
View table structure
Delete table

Insert records
Read records
Read a single record
Update records
Delete records

Filter records
Validate data types
Persist data to disk
```

---

# 3. Example

The user creates a database:

```http
POST /api/databases
```

Request:

```json
{
  "name": "college"
}
```

MiniDB should create something similar to:

```text
minidb-data/
└── databases/
    └── college/
```

Then the user creates a table:

```http
POST /api/databases/college/tables
```

Request:

```json
{
  "name": "students",
  "columns": [
    {
      "name": "id",
      "dataType": "INT",
      "primaryKey": true
    },
    {
      "name": "name",
      "dataType": "VARCHAR",
      "primaryKey": false
    },
    {
      "name": "age",
      "dataType": "INT",
      "primaryKey": false
    },
    {
      "name": "department",
      "dataType": "VARCHAR",
      "primaryKey": false
    }
  ]
}
```

MiniDB should create:

```text
minidb-data/
└── databases/
    └── college/
        └── students/
```

---

# 4. File-Based Storage

This is one of the most important requirements.

The database must store its data in files.

Use a configurable directory:

```properties
minidb.storage.path=./minidb-data
```

The directory structure should look approximately like:

```text
minidb-data/
│
└── databases/
    │
    ├── college/
    │   │
    │   ├── database.meta
    │   │
    │   ├── students/
    │   │   ├── table.meta
    │   │   └── data.dat
    │   │
    │   └── teachers/
    │       ├── table.meta
    │       └── data.dat
    │
    └── company/
        │
        └── employees/
            ├── table.meta
            └── data.dat
```

Explain clearly what every file does.

---

# 5. Metadata

Each database and table should have metadata.

For example:

```text
database.meta
```

could contain:

```json
{
  "name": "college"
}
```

A table's:

```text
table.meta
```

could contain:

```json
{
  "name": "students",
  "columns": [
    {
      "name": "id",
      "dataType": "INT",
      "primaryKey": true
    },
    {
      "name": "name",
      "dataType": "VARCHAR",
      "primaryKey": false
    },
    {
      "name": "age",
      "dataType": "INT",
      "primaryKey": false
    }
  ]
}
```

Metadata should describe the structure of the database.

---

# 6. Record Storage

Store actual records separately from metadata.

For Version 1, use a **simple JSON-lines or similar beginner-friendly file format**.

Example:

```text
data.dat
```

contains:

```json
{"id":1,"name":"Rahul","age":21}
{"id":2,"name":"Priya","age":22}
{"id":3,"name":"Amit","age":20}
```

Each line represents one record.

Explain why metadata and record data are stored separately.

Do not use Java serialization.

Do not use an external database.

---

# 7. Data Types

Initially support only these data types:

```text
INT
VARCHAR
DOUBLE
BOOLEAN
DATE
```

Create:

```java
public enum DataType {
    INT,
    VARCHAR,
    DOUBLE,
    BOOLEAN,
    DATE
}
```

Do not implement complicated data types.

---

# 8. Database Management

Implement:

```text
POST   /api/databases
GET    /api/databases
GET    /api/databases/{databaseName}
DELETE /api/databases/{databaseName}
```

Example:

```http
POST /api/databases
```

```json
{
  "name": "college"
}
```

The service should physically create:

```text
./minidb-data/databases/college/
```

---

# 9. Table Management

Implement:

```text
POST   /api/databases/{databaseName}/tables
GET    /api/databases/{databaseName}/tables
GET    /api/databases/{databaseName}/tables/{tableName}
DELETE /api/databases/{databaseName}/tables/{tableName}
```

Creating a table should physically create:

```text
./minidb-data/databases/college/students/
```

and:

```text
table.meta
data.dat
```

---

# 10. Insert Records

Implement:

```text
POST /api/databases/{databaseName}/tables/{tableName}/rows
```

Example:

```json
{
  "id": 1,
  "name": "Rahul",
  "age": 21,
  "department": "CSE"
}
```

The database engine should:

1. Find the database
2. Find the table
3. Read table metadata
4. Validate the columns
5. Validate data types
6. Check primary key
7. Write the record to the table's data file

---

# 11. Read Records

Implement:

```text
GET /api/databases/{databaseName}/tables/{tableName}/rows
```

Return:

```json
[
  {
    "id": 1,
    "name": "Rahul",
    "age": 21,
    "department": "CSE"
  },
  {
    "id": 2,
    "name": "Priya",
    "age": 22,
    "department": "ECE"
  }
]
```

The records must come from the **file stored by MiniDB**.

Do not use an in-memory list as the permanent database.

---

# 12. Persistence Requirement

This is critical.

If the application is running:

```text
Insert record
     ↓
data.dat
```

Then stop the Spring Boot application.

Start it again.

The records must still exist.

Example:

```text
Application Start
       ↓
Read existing files
       ↓
Load database metadata when required
       ↓
Access existing records
```

Demonstrate this with an example.

---

# 13. Get One Record

Implement:

```text
GET /api/databases/{databaseName}/tables/{tableName}/rows/{id}
```

Example:

```text
GET /api/databases/college/tables/students/1
```

Return:

```json
{
  "id": 1,
  "name": "Rahul",
  "age": 21,
  "department": "CSE"
}
```

---

# 14. Update Record

Implement:

```text
PUT /api/databases/{databaseName}/tables/{tableName}/rows/{id}
```

Example:

```json
{
  "name": "Rahul Kumar",
  "age": 22,
  "department": "CSE"
}
```

The database engine should:

1. Read the file
2. Find the record
3. Modify the record
4. Rewrite the file safely

Keep the implementation simple.

---

# 15. Delete Record

Implement:

```text
DELETE /api/databases/{databaseName}/tables/{tableName}/rows/{id}
```

The record should actually be removed from the stored data file.

---

# 16. Primary Key

Support one primary key per table.

Example:

```text
id INT PRIMARY KEY
```

When inserting:

```json
{
  "id": 1,
  "name": "Rahul"
}
```

and ID 1 already exists, return:

```text
409 CONFLICT
```

with:

```json
{
  "message": "Duplicate primary key value: 1"
}
```

Do not implement composite primary keys.

---

# 17. Data Validation

Before storing a record, validate it against the table metadata.

Example:

Metadata:

```text
age INT
```

Invalid:

```json
{
  "age": "twenty"
}
```

Return:

```json
{
  "message": "Invalid value for column 'age'. Expected INT."
}
```

Also validate:

```text
Missing required columns
Unknown columns
Invalid data types
Duplicate primary key
```

Keep validation code simple and readable.

---

# 18. Basic Filtering

Implement basic filtering:

```http
GET /api/databases/college/tables/students/rows?column=age&operator=GT&value=20
```

Support:

```text
EQ
GT
LT
GTE
LTE
```

Example:

```text
age > 20
```

Return matching records.

Do not implement:

```text
JOIN
GROUP BY
HAVING
ORDER BY
Subqueries
Aggregations
Complex expressions
```

---

# 19. Simple Query Engine

Optionally implement a very small query endpoint:

```text
POST /api/query
```

Request:

```json
{
  "query": "SELECT * FROM students"
}
```

Initially support only:

```text
SELECT * FROM table
```

Then optionally:

```text
SELECT * FROM students WHERE age > 20
```

Do NOT build a complete SQL parser.

Use simple parsing techniques such as:

```text
split
trim
startsWith
contains
```

Keep the parser understandable to a beginner.

The REST APIs must work independently of the query parser.

---

# 20. Storage Engine

Create a dedicated storage layer.

For example:

```text
storage/
├── FileStorage.java
├── DatabaseStorage.java
├── TableStorage.java
└── RecordStorage.java
```

Responsibilities:

### FileStorage

Handle:

```text
create directory
create file
read file
write file
delete file
```

### DatabaseStorage

Handle database directories.

### TableStorage

Handle table directories and metadata.

### RecordStorage

Handle:

```text
insert
read
update
delete
```

Do not mix REST controller logic with file operations.

---

# 21. Recommended Architecture

Use:

```text
Controller
     ↓
Service
     ↓
Database Engine
     ↓
Storage Engine
     ↓
File System
```

For example:

```text
RowController
     ↓
RowService
     ↓
DatabaseEngine
     ↓
RecordManager
     ↓
FileStorage
     ↓
data.dat
```

---

# 22. Project Structure

Use a simple structure:

```text
src/main/java/com/example/minidb/

├── MiniDbApplication.java
│
├── controller/
│   ├── DatabaseController.java
│   ├── TableController.java
│   └── RowController.java
│
├── service/
│   ├── DatabaseService.java
│   ├── TableService.java
│   └── RowService.java
│
├── engine/
│   ├── DatabaseEngine.java
│   ├── TableEngine.java
│   └── QueryEngine.java
│
├── storage/
│   ├── FileStorage.java
│   ├── DatabaseStorage.java
│   ├── TableStorage.java
│   └── RecordStorage.java
│
├── model/
│   ├── Database.java
│   ├── Table.java
│   ├── Column.java
│   └── Record.java
│
├── dto/
│   ├── CreateDatabaseRequest.java
│   ├── CreateTableRequest.java
│   ├── ColumnRequest.java
│   └── QueryRequest.java
│
├── enums/
│   └── DataType.java
│
├── validator/
│   └── DataValidator.java
│
├── exception/
│   ├── DatabaseNotFoundException.java
│   ├── TableNotFoundException.java
│   ├── RecordNotFoundException.java
│   ├── DuplicateKeyException.java
│   └── GlobalExceptionHandler.java
│
└── config/
    └── StorageConfig.java
```

You may modify the structure slightly if necessary, but keep it simple.

---

# 23. Important Rule: No In-Memory-Only Database

Do NOT implement the database like:

```java
Map<String, List<Map<String, Object>>> database;
```

and consider that the database.

You may temporarily use Java collections while processing records, but the **source of truth must be the files on disk**.

For example:

```text
data.dat
   ↓
read
   ↓
List<Record>
   ↓
modify
   ↓
write
   ↓
data.dat
```

---

# 24. Important Rule: Generic Tables

Do not create:

```text
Student.java
Employee.java
Product.java
```

The user creates the table dynamically.

For example:

```text
CREATE TABLE students
```

or through:

```http
POST /api/databases/college/tables
```

The engine should work with any valid table name and column definitions.

---

# 25. File Safety

Implement basic safe file handling.

When updating a file:

```text
Read original file
       ↓
Modify records
       ↓
Write updated content
```

Prefer writing to a temporary file first and then replacing the original file if this can be done simply.

Do not implement advanced WAL/recovery systems.

---

# 26. Naming Rules

Validate database and table names.

Allow names such as:

```text
college
students
employee_data
products
```

Reject invalid names containing unsafe path characters such as:

```text
../
../../
```

Do not allow users to escape the MiniDB storage directory.

---

# 27. Error Handling

Implement:

```java
@RestControllerAdvice
public class GlobalExceptionHandler
```

Handle:

```text
Database not found
Table not found
Record not found
Duplicate database
Duplicate table
Duplicate primary key
Invalid data type
Invalid column
Invalid database/table name
Invalid request
```

Return simple JSON errors:

```json
{
  "status": 404,
  "message": "Database 'college' not found"
}
```

---

# 28. No Authentication

Do not implement:

```text
Login
Register
JWT
Roles
Permissions
```

Anyone can use the local database server.

---

# 29. No Docker

The application should run directly using:

```bash
mvn spring-boot:run
```

or:

```bash
mvn clean package
java -jar target/minidb.jar
```

---

# 30. Configuration

Create:

```properties
server.port=8080

minidb.storage.path=./minidb-data
```

Explain that changing this property changes where MiniDB stores its databases.

---



# 32. Postman Testing

Provide a complete testing sequence:

```text
1. Create Database
2. List Databases

3. Create Table
4. List Tables
5. View Table Structure

6. Insert Record
7. Insert Second Record
8. Get All Records
9. Get Single Record

10. Filter Records

11. Update Record
12. Delete Record

13. Delete Table
14. Delete Database
```

Provide complete URLs, JSON bodies, expected responses, and HTTP status codes.

---

# 33. Sample Database

Use this example throughout the documentation:

```text
Database:
college

Table:
students

Columns:

id          INT       PRIMARY KEY
name        VARCHAR
age         INT
cgpa        DOUBLE
active      BOOLEAN
joiningDate DATE
```

Sample records:

```json
{
  "id": 1,
  "name": "Rahul",
  "age": 21,
  "cgpa": 8.5,
  "active": true,
  "joiningDate": "2026-07-01"
}
```

```json
{
  "id": 2,
  "name": "Priya",
  "age": 22,
  "cgpa": 9.1,
  "active": true,
  "joiningDate": "2026-07-02"
}
```

---

# 34. Beginner-Friendly Coding Rules

This is extremely important.

Write simple Java code.

Prefer:

```text
clear classes
clear methods
normal if/else
normal loops
simple collections
small methods
meaningful variable names
```

Avoid unnecessarily advanced:

```text
reflection
complex generics
advanced concurrency
reactive programming
functional programming everywhere
complex design patterns
byte-level optimization
```

Use Java NIO:

```java
Path
Paths
Files
```

for file operations.

---

# 35. SOLID Principles

Follow basic SOLID principles where they make sense.

For example:

```text
FileStorage
```

should be responsible for file operations.

```text
RecordStorage
```

should be responsible for record persistence.

```text
DataValidator
```

should validate data.

```text
RowService
```

should coordinate row operations.

Do NOT create dozens of interfaces just to claim SOLID.

Keep the architecture understandable.

---

# 36. What NOT to Implement

Do NOT implement these in Version 1:

```text
B+ Tree
B Tree
Indexes
Query Optimizer
Transactions
ACID
Write-Ahead Logging
Crash Recovery
Replication
Sharding
Concurrency Control
MVCC
Foreign Keys
JOIN
GROUP BY
HAVING
Stored Procedures
Triggers
Views
User Authentication
Distributed Storage
```

These can be future enhancements.

---

# 37. Future Enhancement Section

After completing Version 1, explain how the database could evolve.

Possible future versions:

### Version 2

```text
UPDATE optimization
DELETE optimization
ORDER BY
LIMIT
simple indexing
```

### Version 3

```text
Page-based storage
Binary records
Buffer manager
```

### Version 4

```text
B+ Tree indexes
```

### Version 5

```text
Transactions
WAL
Crash recovery
```

But do NOT implement these now.

---

# 38. Complete Project Requirement

Generate a **fully working Spring Boot Java 17 project**.

Do not provide pseudo-code.

Do not omit classes.

Do not say:

```text
"implement similarly"
```

Do not say:

```text
"remaining code is left as an exercise"
```

Provide all required imports.

Every class should compile.

---

# 39. Final Explanation

After generating the code, explain this flow in beginner-friendly language:

```text
POST /api/databases
        ↓
DatabaseController
        ↓
DatabaseService
        ↓
DatabaseEngine
        ↓
DatabaseStorage
        ↓
FileStorage
        ↓
File System
        ↓
./minidb-data/databases/college/
```

And for inserting a record:

```text
POST /rows
      ↓
RowController
      ↓
RowService
      ↓
DataValidator
      ↓
RecordStorage
      ↓
data.dat
```

Explain exactly:

1. Where the data is stored
2. Which file contains metadata
3. Which file contains actual records
4. How records are written
5. How records are read
6. How records survive application restart
7. How tables are represented
8. How databases are represented

---

# 40. Final Success Criteria

The project is considered complete only when this works:

```text
Start MiniDB
      ↓
Create database "college"
      ↓
Create table "students"
      ↓
Define columns
      ↓
Insert records
      ↓
Records are written to ./minidb-data/
      ↓
Read records
      ↓
Update records
      ↓
Delete records
      ↓
Stop application
      ↓
Start application again
      ↓
Previously stored records still exist
```

The final result should be a **small, understandable, file-based database system written in Java 17**, exposed through Spring Boot REST APIs.

The purpose is educational: understand the basic relationship between a database, tables, records, metadata, storage, and a query layer without depending on MySQL or another database system.
