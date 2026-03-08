# HexVG-DatabaseAddon


HexVG-DatabaseAddon is a Skript addon built for the VenomGrave server. Me and a friend spent quite a bit of time working on this — a lot of effort went into making everything work properly, especially the asynchronous query handling. At some point we had to rewrite the entire architecture to get rid of deadlocks that were causing the server to freeze while waiting for database responses.

The plugin lets you write Skript scripts that communicate with a MySQL or SQLite database without any knowledge of Java. All the connection handling, HikariCP connection pooling and error management is done on the plugin side — in Skript you just write what you want to do with the data.

---

## Features

- **MySQL** and **SQLite** support
- Fully **asynchronous** queries — the server never freezes waiting for the database
- **SQL injection** protection via PreparedStatement
- Table and column name validation
- Per-player **result cache**
- **Debug mode** with query logging and execution time
- Clean and readable Skript syntax

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Paper | 1.16.5+ |
| Skript | 2.6+ |
| Java | 11+ |

No additional libraries needed on the server side — HikariCP, sqlite-jdbc and mysql-connector are all shaded inside the jar.

---

## Installation

1. Drop `HexVG-DatabaseAddon.jar` into your `plugins/` folder
2. Start the server — the plugin will generate `config.yml`
3. Configure your database connection
4. Restart the server

---

## Configuration

```yaml
debug: false

database:
  type: SQLITE   # SQLITE or MYSQL

  sqlite:
    file: database.db

  mysql:
    host: localhost
    port: 3306
    database: your_database
    username: root
    password: ""
    pool-size: 5
```

> When using MySQL, make sure to create the database manually beforehand: `CREATE DATABASE your_database;`

---

## Skript Syntax

### Execute a query

```skript
execute db query "SELECT * FROM players WHERE uuid = ?" with values {_uuid}
wait 2 ticks
set {_coins} to column "coins" from row 1 of last db query result
```

### Insert a record

```skript
set {_cols::1} to "uuid"
set {_cols::2} to "name"
set {_cols::3} to "coins"
set {_vals::1} to {_uuid}
set {_vals::2} to player's name
set {_vals::3} to "0"
db insert into table "players" columns {_cols::*} values {_vals::*}
```

### Update data

```skript
db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
```

### Delete a record

```skript
db delete from table "players" where "uuid" = {_uuid}
```

### Check if a table exists

```skript
check db table "players"
wait 20 ticks
if db table "players" doesn't exist:
    execute db query "CREATE TABLE IF NOT EXISTS players (...)"
```

### Read query results

```skript
set {_rows} to db row count of last db query result
set {_name} to column "name" from row 1 of last db query result
```

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/hexvgdb status` | Shows database connection status | `hexvg.database.admin` |
| `/hexvgdb debug` | Toggles debug mode on/off | `hexvg.database.admin` |
| `/hexvgdb reload` | Reloads the configuration | `hexvg.database.admin` |

---

## Important — wait ticks

Since all queries run asynchronously, you need to give the database a moment to respond before reading the result. In practice `wait 2 ticks` is enough for regular queries, and `wait 20 ticks` is recommended when checking tables on server startup.

```skript
execute db query "SELECT ..."
wait 2 ticks               # give the async query time to complete
set {_val} to column "..." from row 1 of last db query result
```

---

## Example Script

The repository includes an `example.sk` file with a ready-to-use coin system that demonstrates all plugin features — table creation, SELECT, INSERT, UPDATE, DELETE and player commands.

---

## Authors

Built for the **VenomGrave** server by the HexVG team.
