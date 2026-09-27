# HexVG-DatabaseAddon

> A Skript addon for database operations, built for the **VenomGrave** server

[🇵🇱 Polski](README-PL.md) | 🇬🇧 English

![version](https://img.shields.io/badge/version-1.2.0-blue)
![paper](https://img.shields.io/badge/Paper-1.21.x%20%7C%2026.x-green)
![java](https://img.shields.io/badge/Java-21%2B-orange)
![skript](https://img.shields.io/badge/Skript-2.6.4%2B-purple)
![papi](https://img.shields.io/badge/PlaceholderAPI-optional-yellow)
![license](https://img.shields.io/badge/license-MIT-gray)

---

## About

HexVG-DatabaseAddon is a Skript addon created for the VenomGrave server. A lot of work went into making queries behave properly. At one point we had to rewrite the whole query system to get rid of deadlocks that froze the server. In the latest version queries run on the plugin's own thread pool, so the result is ready on the very next line of your script, with no `wait` needed.

The plugin lets you write scripts that talk to MySQL or SQLite without any knowledge of Java. The connection, HikariCP connection pooling, error handling, transaction rollbacks, player locks and table creation are all handled by the plugin. In Skript you only describe what you want to do with the data.

---

## Features

- Supports **MySQL**, **MariaDB** and **SQLite**
- Queries run on a **separate thread pool**, so there is **no need for `wait`** before reading the result
- **Transactions** with automatic rollback. An error in any query undoes **everything**, with no partial writes
- **Double-spend protection**: a second concurrent transaction for the same player won't start
- **Player lock system**: prevents race conditions when a command is spammed
- **Guaranteed table creation**: `db ensure table` waits until the table exists, with no race conditions on startup
- **PlaceholderAPI integration**: database values available in scoreboards, tab lists and holograms
- **SQL injection** protection via PreparedStatement
- Table and column name validation
- **Query results kept separately for each player** (commands, `on join`, `on death`, GUIs…)
- **Automatic cleanup**: abandoned transactions and locks expire after 30 s, and a player quitting rolls back their transaction
- **Automatic reconnect** after a MySQL restart
- **Debug mode** logging every query with its execution time
- All libraries bundled in the jar, no extra dependencies

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Paper | 1.21.x / 26.x |
| Skript | 2.6.4+ (tested on 2.15 and 2.16.2) |
| Java | 21+ |
| PlaceholderAPI | optional |

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
    database: database_name
    username: root
    password: ""
    pool-size: 5   # 1-20, MySQL only
```

---

## Skript syntax

### Creating a table (recommended)

Waits until the table is created. Safe in `on skript load`, with no `wait ticks` and no race conditions, even when several players join at the same time.

```skript
on skript load:
    db ensure table "players" with query "CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16), coins INT DEFAULT 0)"
```

### Reading data

The result is available right after the query.

```skript
execute db query "SELECT * FROM players WHERE uuid = ?" with values {_uuid}
set {_coins} to column "coins" from row 1 of last db query result
set {_rows} to db row count of last db query result
set {_names::*} to all db values of column "name" from last db query result
```

> Rows are numbered from **1**. If a query fails, the result is empty (0 rows), not the one from the previous query.

### Inserting a record

```skript
db insert into table "players" columns "uuid" and "coins" values {_uuid} and "0"
```

Or with lists:

```skript
set {_cols::1} to "uuid"
set {_cols::2} to "coins"
set {_vals::1} to {_uuid}
set {_vals::2} to "0"
db insert into table "players" columns {_cols::*} values {_vals::*}
```

### Updating and deleting

```skript
db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
db delete from table "players" where "uuid" = {_uuid}
```

### Transactions

Several queries as one atomic operation: either everything is saved, or nothing is.

```skript
db begin transaction

db update table "players" set "coins" to "%{_new}%" where "uuid" = {_uuid}
db insert into table "purchases" columns {_cols::*} values {_vals::*}

db commit transaction

if last db transaction failed:
    send "&cSomething went wrong. Your coins were not taken." to player
    stop

send "&aPurchase completed!" to player
```

> **Don't use `wait` between `db begin transaction` and `db commit transaction`.** With SQLite, every other query waits for the transaction to finish during that time.

What happens on errors:

- A failed query or an invalid table/column name inside a transaction → **full rollback**. Subsequent queries up to `db commit transaction` are skipped.
- If `db begin transaction` fails (e.g. this player already has an active transaction), **the rest of the script is not executed**.
- A transaction left open for more than 30 s (e.g. `stop` before commit) is rolled back automatically. A player leaving the server also rolls back their transaction.

### Player lock

Prevents a command from running again before the previous run has finished.

```skript
if player is db locked:
    send "&cPlease wait before using this command again." to player
    stop
db lock player

# ... queries ...

db unlock player
```

> The lock expires on its own after 30 s, in case the script never reaches `db unlock`.

### Checking a table

```skript
check db table "players"
if db table "players" exists:
    send "The table exists"
```

### PlaceholderAPI

If PlaceholderAPI is installed, the expansion registers automatically. Set a value from Skript after a query and it works anywhere PAPI is supported.

```skript
execute db query "SELECT coins FROM players WHERE uuid = ?" with values {_uuid}
set {_coins} to column "coins" from row 1 of last db query result
db set placeholder "coins" to "%{_coins}%" for player
```

| Placeholder | Description |
|---|---|
| `%hexvgdb_<key>%` | value set with `db set placeholder` |
| `%hexvgdb_connected%` | `true` / `false`, database connection status |
| `%hexvgdb_locked%` | `true` / `false`, whether the player has an active lock |

Placeholder values are cleared when a player leaves, so set them in `on join`.

---

## Important: performance

- **`wait` is no longer needed.** Every database effect waits for its result, so the next line of the script already has the data.
- While a query runs, the script pauses the server tick. With SQLite and a local MySQL this is usually milliseconds (200 queries ≈ 0.08 s in tests).
- If the database stops responding, a query fails after about **4 s**. The server doesn't hang permanently, and once the database is back the plugin reconnects on its own.
- **Always** pass player input through `with values`, never by concatenating it into SQL.
- MySQL 8: if you get "Public Key Retrieval is not allowed", use a user with `mysql_native_password` or an SSL connection.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/hexvgdb status` | Database connection status | `hexvg.database.admin` |
| `/hexvgdb debug` | Toggles debug mode | `hexvg.database.admin` |
| `/hexvgdb reload` | Reloads the configuration | `hexvg.database.admin` |

Available to operators only by default.

---

## Example scripts

The repository contains two examples:

- [`example.sk`](../example.sk): a coin system with SELECT, INSERT, UPDATE, DELETE, transactions and locks
- [`example_papi.sk`](../example_papi.sk): a stats system (coins, kills, rank) with full PlaceholderAPI integration

---

## Authors

Created for the **VenomGrave** server by HexVG Team.  
Bugs and suggestions: https://github.com/VenomGrave/HexVG-DatabaseAddon/issues
